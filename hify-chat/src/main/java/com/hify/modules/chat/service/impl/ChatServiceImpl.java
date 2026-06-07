package com.hify.modules.chat.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hify.common.exception.BizException;
import com.hify.common.exception.ErrorCode;
import com.hify.common.exception.LlmApiException;
import com.hify.common.http.LlmHttpClient;
import com.hify.modules.agent.entity.Agent;
import com.hify.modules.agent.mapper.AgentMapper;
import com.hify.modules.chat.dto.MessageResp;
import com.hify.modules.chat.dto.SessionCreateReq;
import com.hify.modules.chat.dto.SessionResp;
import com.hify.modules.chat.entity.ChatMessage;
import com.hify.modules.chat.entity.ChatSession;
import com.hify.modules.chat.mapper.ChatMessageMapper;
import com.hify.modules.chat.mapper.ChatSessionMapper;
import com.hify.modules.chat.service.ChatContextCache;
import com.hify.modules.chat.service.ChatMessageWriteService;
import com.hify.modules.chat.service.ChatService;
import com.hify.modules.provider.adapter.ProviderAdapter;
import com.hify.modules.provider.adapter.ProviderAdapterFactory;
import com.hify.modules.provider.dto.ChatRequest;
import com.hify.modules.provider.dto.ChatStreamChunk;
import com.hify.modules.provider.entity.ModelConfig;
import com.hify.modules.provider.entity.Provider;
import com.hify.modules.provider.mapper.ModelConfigMapper;
import com.hify.modules.provider.mapper.ProviderMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 对话引擎（有状态）。会话显式创建，消息按 sessionId 关联。
 * <p>
 * 事务边界：消息写入委托 {@link ChatMessageWriteService}（独立 Bean 秒级提交），
 * {@code sendMessage} 编排方法无事务，LLM 流式 IO 不占 DB 连接。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatServiceImpl implements ChatService {

    /** SSE 总超时：单次对话最长 120 秒内必须完成 LLM 输出 */
    private static final long SSE_TIMEOUT_MS = 120_000L;

    private final LlmHttpClient llmHttpClient;
    private final AgentMapper agentMapper;
    private final ModelConfigMapper modelConfigMapper;
    private final ProviderMapper providerMapper;
    private final ChatSessionMapper chatSessionMapper;
    private final ChatMessageMapper chatMessageMapper;
    private final ChatMessageWriteService messageWriteService;
    private final ChatContextCache contextCache;
    private final ProviderAdapterFactory adapterFactory;
    private final ObjectMapper objectMapper;

    /** 流式 SSE 专用线程池（AbortPolicy：满载抛 RejectedExecutionException，上层转 503）。 */
    @Qualifier("llmStreamExecutor")
    private final ThreadPoolExecutor llmStreamExecutor;

    // ════════════════════════════════════════════════════════════════
    // 会话 CRUD
    // ════════════════════════════════════════════════════════════════

    @Override
    @Transactional
    public SessionResp createSession(SessionCreateReq req) {
        // 校验 agent 存在
        requireAgent(req.getAgentId());

        ChatSession session = new ChatSession();
        session.setAgentId(req.getAgentId());
        session.setTitle(req.getTitle() != null && !req.getTitle().isBlank()
                ? req.getTitle() : "新对话");
        session.setStatus("ACTIVE");
        chatSessionMapper.insert(session);
        log.info("会话创建 id={} agentId={}", session.getId(), session.getAgentId());
        return SessionResp.from(session);
    }

    @Override
    public List<SessionResp> listSessions(Long agentId) {
        LambdaQueryWrapper<ChatSession> wrapper = new LambdaQueryWrapper<>();
        if (agentId != null) {
            wrapper.eq(ChatSession::getAgentId, agentId);
        }
        wrapper.orderByDesc(ChatSession::getUpdatedAt);
        return chatSessionMapper.selectList(wrapper).stream()
                .map(SessionResp::from)
                .toList();
    }

    @Override
    public List<MessageResp> listMessages(Long sessionId, Long beforeId, int size) {
        requireSession(sessionId);
        // 游标分页：beforeId 为上一页最小 id，倒序取 size 条（禁止 LIMIT offset）
        LambdaQueryWrapper<ChatMessage> wrapper = new LambdaQueryWrapper<ChatMessage>()
                .eq(ChatMessage::getSessionId, sessionId);
        if (beforeId != null) {
            wrapper.lt(ChatMessage::getId, beforeId);
        }
        wrapper.orderByDesc(ChatMessage::getId).last("LIMIT " + size);
        return chatMessageMapper.selectList(wrapper).stream()
                .map(MessageResp::from)
                .toList();
    }

    @Override
    @Transactional
    public void deleteSession(Long sessionId) {
        requireSession(sessionId);
        // 消息物理删除，会话逻辑删除
        chatMessageMapper.delete(new LambdaQueryWrapper<ChatMessage>()
                .eq(ChatMessage::getSessionId, sessionId));
        chatSessionMapper.deleteById(sessionId);
        contextCache.evict(sessionId); // 清理工作内存
        log.info("会话删除 id={}（消息物理删除，会话逻辑删除，缓存已清理）", sessionId);
    }

    // ════════════════════════════════════════════════════════════════
    // 流式对话：编排方法，无事务
    // ════════════════════════════════════════════════════════════════

    @Override
    public SseEmitter sendMessage(Long sessionId, String content) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        // 停止标志：被 onTimeout / 客户端断开 / 连接错误置位，作为 streamCancellable 的取消信号
        AtomicBoolean stop = new AtomicBoolean(false);

        // ── SSE 超时：onTimeout 回调置位停止标志，中止 LLM 读取，并完成 emitter ──
        emitter.onTimeout(() -> {
            log.warn("SSE 超时（{}ms），中止 LLM 调用 sessionId={}", SSE_TIMEOUT_MS, sessionId);
            stop.set(true);
            emitter.complete();
        });
        // ── 连接错误 / 客户端提前关闭：置位停止标志，让流式循环尽快退出 ──
        emitter.onError(t -> {
            log.debug("SSE 连接错误，中止 LLM 调用 sessionId={}", sessionId);
            stop.set(true);
        });
        emitter.onCompletion(() -> stop.set(true));

        // 异步执行，立即返回 emitter 释放 HTTP 线程；
        // 线程池 AbortPolicy 满载时抛 RejectedExecutionException → 由上层转 503
        llmStreamExecutor.execute(() -> doStream(sessionId, content, emitter, stop));
        return emitter;
    }

    /**
     * 流式对话实际执行体（运行在 llmStreamExecutor 线程）。无事务：消息写入全部委托
     * {@link ChatMessageWriteService} 的独立事务方法，DB 连接不跨越 LLM 流式 IO。
     */
    private void doStream(Long sessionId, String content, SseEmitter emitter, AtomicBoolean stop) {
        long startMs = System.currentTimeMillis();
        String fullContent = "";
        // 记录 send 抛出的 IOException（客户端断开）：循环结束后据此 completeWithError
        AtomicReference<IOException> sendError = new AtomicReference<>();

        try {
            // ── 第一步：会话必须已存在，反查 agentId ──
            ChatSession session = requireSession(sessionId);

            // ── 第二步：保存用户消息（独立事务），并据首条消息生成会话标题 ──
            messageWriteService.saveUserMessage(sessionId, content);
            updateSessionTitleIfFirst(session, content);

            // ── 第三步：读配置（Agent → Model → Provider） ──
            Agent agent = requireAgent(session.getAgentId());
            ModelConfig modelConfig = requireModelConfig(agent.getModelConfigId());
            Provider provider = requireProvider(modelConfig.getProviderId());

            // ── 第四步：拼装 LLM 请求 ──
            ProviderAdapter adapter = adapterFactory.get(provider.getType());
            String url = adapter.buildChatUrl(provider.getBaseUrl(), modelConfig.getModelId());
            Map<String, String> headers = adapter.buildAuthHeaders(provider);

            ChatRequest chatReq = new ChatRequest();
            chatReq.setModel(modelConfig.getModelId());
            chatReq.setMessages(buildMessages(agent, sessionId, content));
            chatReq.setTemperature(agent.getTemperature());
            chatReq.setMaxTokens(agent.getMaxTokens());
            chatReq.setStream(true);
            String body = adapter.buildChatRequestBody(chatReq);

            // ── 第五步：流式调 LLM（stop 为取消信号，置位即中断 HTTP 读取） ──
            StringBuilder sb = new StringBuilder();
            llmHttpClient.streamCancellable(url, headers, body,
                    rawLine -> {
                        ChatStreamChunk chunk = adapter.parseStreamLine(rawLine);
                        if (chunk == null || chunk.getContent() == null) {
                            return;
                        }
                        sb.append(chunk.getContent());
                        sendChunk(emitter, chunk.getContent(), stop, sendError);
                    },
                    stop::get
            );
            fullContent = sb.toString();

            // ── 第六步（异常路径）：客户端断开（send 失败）→ 保存部分回复并 completeWithError ──
            if (sendError.get() != null) {
                savePartialReply(sessionId, fullContent, "error",
                        (int) (System.currentTimeMillis() - startMs));
                emitter.completeWithError(sendError.get());
                return;
            }

            // ── 第七步（异常路径）：被 onTimeout 中止 → emitter 已 complete，仅保存部分回复 ──
            if (stop.get()) {
                savePartialReply(sessionId, fullContent, "length",
                        (int) (System.currentTimeMillis() - startMs));
                return;
            }

            // ── 第八步：正常结束，保存 assistant 回复（独立事务） ──
            if (!fullContent.isEmpty()) {
                messageWriteService.saveAssistantMessage(sessionId, fullContent,
                        countTokens(fullContent), "stop",
                        (int) (System.currentTimeMillis() - startMs));

                // 追加本轮对话到工作内存（滑动窗口），供下轮直接取用
                int maxTurns = agent.getMaxContextTurns() != null ? agent.getMaxContextTurns() : 10;
                contextCache.appendTurn(sessionId,
                        message("user", content),
                        message("assistant", fullContent),
                        maxTurns);
            }

            // 正常完成：发送 done 事件，前端据此移除加载态（与连接中断相区分）
            try {
                emitter.send(SseEmitter.event().name("done").data("{}"));
            } catch (IOException ignore) {
                // 客户端在收尾时已断开，无需处理
            }
            emitter.complete();

        } catch (LlmApiException e) {
            log.warn("LLM 调用失败 sessionId={}", sessionId, e);
            savePartialReply(sessionId, fullContent, "error",
                    (int) (System.currentTimeMillis() - startMs));
            emitter.completeWithError(e);
        } catch (Exception e) {
            log.error("对话异常 sessionId={}", sessionId, e);
            savePartialReply(sessionId, fullContent, "error",
                    (int) (System.currentTimeMillis() - startMs));
            emitter.completeWithError(e);
        }
    }

    // ════════════════════════════════════════════════════════════════
    // 校验
    // ════════════════════════════════════════════════════════════════

    private ChatSession requireSession(Long sessionId) {
        ChatSession session = chatSessionMapper.selectById(sessionId);
        if (session == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "会话不存在: " + sessionId);
        }
        return session;
    }

    private Agent requireAgent(Long agentId) {
        Agent agent = agentMapper.selectById(agentId);
        if (agent == null) {
            throw new BizException(ErrorCode.AGENT_NOT_FOUND, "Agent 不存在: " + agentId);
        }
        return agent;
    }

    private ModelConfig requireModelConfig(Long modelConfigId) {
        ModelConfig config = modelConfigMapper.selectById(modelConfigId);
        if (config == null) {
            throw new BizException(ErrorCode.MODEL_CONFIG_NOT_FOUND,
                    "模型配置不存在: " + modelConfigId);
        }
        return config;
    }

    private Provider requireProvider(Long providerId) {
        Provider provider = providerMapper.selectById(providerId);
        if (provider == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "供应商不存在: " + providerId);
        }
        return provider;
    }

    // ════════════════════════════════════════════════════════════════
    // 上下文拼装
    // ════════════════════════════════════════════════════════════════

    /**
     * 构建 LLM 请求的消息列表：system 首条 + 历史 N 轮（Redis 工作内存）+ 当前用户输入。
     * <p>
     * 历史窗口优先取 Redis；未命中则从 PG（source of truth）查最近 N 轮回填缓存。
     * </p>
     */
    private List<ChatRequest.Message> buildMessages(Agent agent, Long sessionId, String userContent) {
        int maxTurns = agent.getMaxContextTurns() != null ? agent.getMaxContextTurns() : 10;

        List<ChatRequest.Message> messages = new ArrayList<>();

        // system 始终第一条
        messages.add(message("system", agent.getSystemPrompt()));

        // 历史窗口：先查 Redis 工作内存
        List<ChatRequest.Message> window = contextCache.getWindow(sessionId);
        if (window == null) {
            // 缓存未命中 → 从 PG 查最近 N 轮回填
            window = loadHistoryFromDb(sessionId, maxTurns);
            contextCache.warmUp(sessionId, window);
        }
        messages.addAll(window);

        // 当前用户输入
        messages.add(message("user", userContent));

        return messages;
    }

    /** 从 PG 查最近 maxTurns 轮历史（正序，最旧在前）。注意：此时用户消息已写入 PG，需排除当前这条。 */
    private List<ChatRequest.Message> loadHistoryFromDb(Long sessionId, int maxTurns) {
        List<ChatMessage> history = chatMessageMapper.selectList(
                new LambdaQueryWrapper<ChatMessage>()
                        .eq(ChatMessage::getSessionId, sessionId)
                        .orderByDesc(ChatMessage::getId)
                        .last("LIMIT " + (maxTurns * 2 + 1)) // +1 排除刚写入的当前 user 消息
        );
        // 移除最新一条（当前 user 消息，已由 buildMessages 末尾单独追加）
        if (!history.isEmpty()) {
            history.remove(0);
        }
        Collections.reverse(history);
        List<ChatRequest.Message> result = new ArrayList<>(history.size());
        for (ChatMessage m : history) {
            result.add(message(m.getRole(), m.getContent()));
        }
        return result;
    }

    private ChatRequest.Message message(String role, String content) {
        ChatRequest.Message m = new ChatRequest.Message();
        m.setRole(role);
        m.setContent(content);
        return m;
    }

    /**
     * 若会话仍是默认标题，用首条用户消息的前 20 字生成标题。
     * <p>
     * 仅凭「标题仍为默认值」即可判定首轮——后续轮次标题已非默认，自然跳过；
     * 无需再查消息数（此前在 assistant 落库后才判断，count 已为 2，导致永远不改）。
     * </p>
     */
    private void updateSessionTitleIfFirst(ChatSession session, String firstContent) {
        if (!"新对话".equals(session.getTitle())) {
            return;
        }
        String title = firstContent == null ? "" : firstContent.strip().replaceAll("\\s+", " ");
        if (title.isEmpty()) {
            return;
        }
        if (title.length() > 20) {
            title = title.substring(0, 20);
        }
        session.setTitle(title);
        chatSessionMapper.updateById(session);
        log.info("会话标题自动生成 id={} title={}", session.getId(), title);
    }

    // ════════════════════════════════════════════════════════════════
    // 辅助
    // ════════════════════════════════════════════════════════════════

    /**
     * 推送一个增量片段。每个片段以 JSON 字符串字面量发送（{@code data:"...\n..."}），
     * 使内容中的换行/引号被转义，避免原始换行破坏 SSE 帧解析（Markdown 必需）。
     * send 抛 IOException 表示客户端断开：记录异常并置位停止标志，
     * 中止后续 LLM 读取（由 {@link #doStream} 在循环结束后据此 completeWithError）。
     */
    private void sendChunk(SseEmitter emitter, String content,
                           AtomicBoolean stop, AtomicReference<IOException> sendError) {
        if (stop.get()) {
            return;
        }
        try {
            // JSON 编码：换行符转义为 \n（两字符），前端按 \n\n 分帧后 JSON.parse 还原
            emitter.send(SseEmitter.event().data(objectMapper.writeValueAsString(content)));
        } catch (JsonProcessingException e) {
            // String 必可序列化，理论不会发生；记录后跳过该片段，不影响整体流式
            log.warn("SSE 片段 JSON 序列化失败，已跳过", e);
        } catch (IOException e) {
            sendError.set(e);
            stop.set(true);
            log.debug("客户端断开，send 失败，中止 LLM 调用");
        }
    }

    private void savePartialReply(Long sessionId, String content, String finishReason, int latencyMs) {
        if (content.isEmpty()) return;
        try {
            messageWriteService.saveAssistantMessage(sessionId, content,
                    countTokens(content), finishReason, latencyMs);
        } catch (Exception e) {
            log.error("保存部分 assistant 消息失败 sessionId={}", sessionId, e);
        }
    }

    /** 简单 token 估算（中文按字数，英文按分词）。生产环境应改用 tokenizer。 */
    private int countTokens(String text) {
        if (text == null || text.isEmpty()) return 0;
        int chineseChars = 0;
        int englishWords = 0;
        StringBuilder buf = new StringBuilder();
        for (char c : text.toCharArray()) {
            Character.UnicodeBlock block = Character.UnicodeBlock.of(c);
            if (block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS
                    || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A) {
                chineseChars++;
                if (!buf.isEmpty()) { englishWords++; buf.setLength(0); }
            } else if (Character.isWhitespace(c)) {
                if (!buf.isEmpty()) { englishWords++; buf.setLength(0); }
            } else {
                buf.append(c);
            }
        }
        if (!buf.isEmpty()) englishWords++;
        return chineseChars + englishWords;
    }
}

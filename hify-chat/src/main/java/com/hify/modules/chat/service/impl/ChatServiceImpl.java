package com.hify.modules.chat.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hify.common.exception.BizException;
import com.hify.common.exception.ErrorCode;
import com.hify.common.exception.LlmApiException;
import com.hify.common.http.EmbeddingClient;
import com.hify.common.http.LlmHttpClient;
import com.hify.modules.agent.entity.Agent;
import com.hify.modules.agent.entity.AgentTool;
import com.hify.modules.agent.mapper.AgentMapper;
import com.hify.modules.agent.mapper.AgentToolMapper;
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
import com.hify.modules.knowledge.repository.ChunkHit;
import com.hify.modules.knowledge.repository.ChunkRepository;
import com.hify.modules.provider.adapter.ProviderAdapter;
import com.hify.modules.workflow.engine.WorkflowEngine;
import com.hify.modules.provider.adapter.ProviderAdapterFactory;
import com.hify.modules.mcp.entity.McpServer;
import com.hify.modules.mcp.entity.McpTool;
import com.hify.modules.mcp.mapper.McpServerMapper;
import com.hify.modules.mcp.mapper.McpToolMapper;
import com.hify.modules.mcp.service.McpClientService;
import com.hify.modules.provider.dto.ChatRequest;
import com.hify.modules.provider.dto.ChatResponse;
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
import java.util.HashMap;
import java.util.LinkedHashMap;
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

    /** RAG 检索返回的 chunk 条数 */
    private static final int RAG_TOP_K = 3;
    /** RAG 命中的最低相似度（余弦相似度，= 1 - 余弦距离），低于此值的结果丢弃。
     *  阈值与 embedding 模型相关：bge-m3 的相关内容相似度通常落在 0.5~0.7，故取 0.5；
     *  若换 OpenAI text-embedding-3 可上调到 0.75 左右。 */
    private static final double RAG_MIN_SIMILARITY = 0.5;

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
    private final ChunkRepository chunkRepository;
    private final EmbeddingClient embeddingClient;
    private final WorkflowEngine workflowEngine;
    private final AgentToolMapper agentToolMapper;
    private final McpServerMapper mcpServerMapper;
    private final McpToolMapper mcpToolMapper;
    private final McpClientService mcpClientService;

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

            // ── 第三步：读 Agent ──
            Agent agent = requireAgent(session.getAgentId());

            // ── 第四步：工作流优先 ──
            if (agent.getWorkflowId() != null) {
                log.info("触发工作流 sessionId={} workflowId={}", sessionId, agent.getWorkflowId());
                try {
                    String result = workflowEngine.execute(agent.getWorkflowId(), content,
                            agent.getModelConfigId(), agent.getKnowledgeBaseId());

                    // 保存 assistant 消息 + 更新上下文（先落库，再推客户端）
                    messageWriteService.saveAssistantMessage(sessionId, result,
                            countTokens(result), "stop",
                            (int) (System.currentTimeMillis() - startMs));
                    int maxTurns = agent.getMaxContextTurns() != null ? agent.getMaxContextTurns() : 10;
                    contextCache.appendTurn(sessionId,
                            message("user", content),
                            message("assistant", result),
                            maxTurns);
                    updateSessionTitleIfFirst(session, content);

                    // 推送工作流结果（一次性完整发送 → done）
                    try {
                        emitter.send(SseEmitter.event().data(objectMapper.writeValueAsString(result)));
                        emitter.send(SseEmitter.event().name("done").data("{}"));
                    } catch (IOException ignore) {}
                    emitter.complete();
                    return;
                } catch (BizException e) {
                    log.warn("工作流执行失败 sessionId={}", sessionId, e);
                    String errMsg = "工作流执行失败：" + e.getMessage();
                    messageWriteService.saveAssistantMessage(sessionId, errMsg,
                            countTokens(errMsg), "error",
                            (int) (System.currentTimeMillis() - startMs));
                    try {
                        emitter.send(SseEmitter.event().data(errMsg));
                        emitter.send(SseEmitter.event().name("done").data("{}"));
                    } catch (IOException ignore) {}
                    emitter.complete();
                    return;
                }
            }

            ModelConfig modelConfig = requireModelConfig(agent.getModelConfigId());
            Provider provider = requireProvider(modelConfig.getProviderId());

            // ── 第五步：拼装 LLM 请求 ──
            ProviderAdapter adapter = adapterFactory.get(provider.getType());
            String url = adapter.buildChatUrl(provider.getBaseUrl(), modelConfig.getModelId());
            Map<String, String> headers = adapter.buildAuthHeaders(provider);

            ChatRequest chatReq = new ChatRequest();
            chatReq.setModel(modelConfig.getModelId());
            chatReq.setMessages(buildMessages(agent, sessionId, content));
            chatReq.setTemperature(agent.getTemperature());
            chatReq.setMaxTokens(agent.getMaxTokens());

            // ── 第五点五步：加载 Agent 绑定的工具列表 ──
            List<ToolBinding> toolBindings = loadToolBindings(agent.getId());

            if (!toolBindings.isEmpty()) {
                // tools 不为空：先走 tool_calls 分支
                chatReq.setTools(buildToolSchemas(toolBindings));
                chatReq.setToolChoice("auto");
                chatReq.setStream(false);
                String body = adapter.buildChatRequestBody(chatReq);

                // 第一次调用 LLM（非流式），判断是否需要调工具
                String firstResp = llmHttpClient.post(url, headers, body);
                ChatResponse chatResp = adapter.parseChatResponse(firstResp);
                List<Map<String, Object>> toolCalls = chatResp != null ? chatResp.getToolCalls() : null;

                if ("tool_calls".equals(chatResp != null ? chatResp.getFinishReason() : null)
                        && toolCalls != null && !toolCalls.isEmpty()) {
                    // ── LLM 要求调工具 ──
                    // 把 assistant 的 tool_calls 追加进历史
                    ChatRequest.Message assistantMsg = new ChatRequest.Message();
                    assistantMsg.setRole("assistant");
                    assistantMsg.setToolCalls(toolCalls);
                    chatReq.getMessages().add(assistantMsg);

                    // 逐个执行工具
                    for (Map<String, Object> tc : toolCalls) {
                        String callId = (String) tc.getOrDefault("id", "");
                        Map<String, Object> func = castMap(tc.get("function"));
                        String toolName = func != null ? (String) func.getOrDefault("name", "") : "";
                        String argsJson = func != null ? (String) func.getOrDefault("arguments", "{}") : "{}";
                        log.info("[ToolCall] sessionId={} tool={} callId={}", sessionId, toolName, callId);

                        String toolResult;
                        try {
                            ToolBinding binding = toolBindings.stream()
                                    .filter(b -> b.toolName.equals(toolName)).findFirst().orElse(null);
                            if (binding == null) {
                                toolResult = "错误：未找到工具 " + toolName;
                            } else {
                                toolResult = mcpClientService.callTool(
                                        mcpServerMapper.selectById(binding.mcpServerId), toolName,
                                        objectMapper.readValue(argsJson, Map.class));
                            }
                        } catch (Exception e) {
                            log.warn("[ToolCall] 工具执行失败 tool={}", toolName, e);
                            toolResult = "工具调用失败：" + e.getMessage();
                        }
                        // 追加 tool 消息
                        ChatRequest.Message toolMsg = new ChatRequest.Message();
                        toolMsg.setRole("tool");
                        toolMsg.setToolCallId(callId);
                        toolMsg.setContent(toolResult);
                        chatReq.getMessages().add(toolMsg);
                    }

                    // 第二次调用 LLM（流式）
                    chatReq.setTools(null);
                    chatReq.setToolChoice(null);
                    chatReq.setStream(true);
                    String secondBody = adapter.buildChatRequestBody(chatReq);

                    StringBuilder sb = new StringBuilder();
                    llmHttpClient.streamCancellable(url, headers, secondBody,
                            rawLine -> {
                                ChatStreamChunk chunk = adapter.parseStreamLine(rawLine);
                                if (chunk == null || chunk.getContent() == null) return;
                                sb.append(chunk.getContent());
                                sendChunk(emitter, chunk.getContent(), stop, sendError);
                            },
                            stop::get
                    );
                    fullContent = sb.toString();
                } else {
                    // 第一次直接返回了内容（不需要调工具），推送即可
                    fullContent = chatResp != null ? chatResp.getContent() : "";
                    if (fullContent != null && !fullContent.isEmpty()) {
                        try {
                            emitter.send(SseEmitter.event().data(objectMapper.writeValueAsString(fullContent)));
                        } catch (IOException ignore) {}
                    }
                }
            } else {
                // tools 为空：和原有逻辑完全一致，一行不改
                chatReq.setStream(true);
                String body = adapter.buildChatRequestBody(chatReq);

                // ── 第六步：流式调 LLM（stop 为取消信号，置位即中断 HTTP 读取） ──
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
            }

            // ── 第七步（异常路径）：客户端断开（send 失败）→ 保存部分回复并 completeWithError ──
            if (sendError.get() != null) {
                savePartialReply(sessionId, fullContent, "error",
                        (int) (System.currentTimeMillis() - startMs));
                emitter.completeWithError(sendError.get());
                return;
            }

            // ── 第八步（异常路径）：被 onTimeout 中止 → emitter 已 complete，仅保存部分回复 ──
            if (stop.get()) {
                savePartialReply(sessionId, fullContent, "length",
                        (int) (System.currentTimeMillis() - startMs));
                return;
            }

            // ── 第九步：正常结束，保存 assistant 回复（独立事务） ──
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
     * 若 Agent 绑定了知识库（{@code knowledgeBaseId} 非空），则在 system prompt 之后注入 RAG 检索结果。
     * </p>
     */
    private List<ChatRequest.Message> buildMessages(Agent agent, Long sessionId, String userContent) {
        int maxTurns = agent.getMaxContextTurns() != null ? agent.getMaxContextTurns() : 10;

        List<ChatRequest.Message> messages = new ArrayList<>();

        // system 始终第一条：未绑定知识库 → 原始 prompt；绑定了 → 原始 prompt + RAG 检索结果
        messages.add(message("system", buildSystemPrompt(agent, userContent)));

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

    /**
     * 构建 system prompt：未绑定知识库直接返回原始 prompt；绑定了则把相似度检索到的
     * chunk 拼接在原始 prompt 之后（topK={@value #RAG_TOP_K}，过滤相似度低于 {@value #RAG_MIN_SIMILARITY}）。
     * 检索失败或无命中时降级为原始 prompt，不影响对话主流程。
     */
    private String buildSystemPrompt(Agent agent, String userContent) {
        String base = agent.getSystemPrompt();
        Long kbId = agent.getKnowledgeBaseId();
        if (kbId == null) {
            log.info("[RAG] agent={} 未绑定知识库，跳过检索", agent.getId());
            return base; // 未绑定知识库，跳过 RAG
        }

        List<ChunkHit> kept;
        try {
            float[] queryVec = embedQuery(userContent);                 // 用户消息向量化
            log.info("[RAG] kbId={} 查询向量化完成 dim={} query=\"{}\"",
                    kbId, queryVec.length, abbreviate(userContent));

            List<ChunkHit> hits = chunkRepository.searchNearest(queryVec, kbId, RAG_TOP_K);
            // 打印每条命中的距离 / 相似度，便于判断是否过阈值
            for (ChunkHit h : hits) {
                double sim = h.getDistance() == null ? Double.NaN : 1.0 - h.getDistance();
                log.info("[RAG] 候选 chunkId={} distance={} similarity={} pass={} content=\"{}\"",
                        h.getId(), h.getDistance(), String.format("%.4f", sim),
                        sim >= RAG_MIN_SIMILARITY, abbreviate(h.getContent()));
            }

            // 相似度 = 1 - 余弦距离；过滤相似度低于阈值的结果
            kept = hits.stream()
                    .filter(h -> h.getDistance() != null && (1.0 - h.getDistance()) >= RAG_MIN_SIMILARITY)
                    .toList();
            log.info("[RAG] kbId={} 召回 {} 条，过阈值(>={}) {} 条",
                    kbId, hits.size(), RAG_MIN_SIMILARITY, kept.size());
        } catch (Exception e) {
            log.warn("RAG 检索失败，降级为无知识库回答 kbId={}", kbId, e);
            return base;
        }

        if (kept.isEmpty()) {
            log.info("[RAG] kbId={} 无命中，按原始 prompt 回答（未注入知识库）", kbId);
            return base; // 没有命中相关资料，按原 prompt 回答
        }

        log.info("[RAG] kbId={} 注入 {} 条参考资料到 system prompt", kbId, kept.size());
        StringBuilder sb = new StringBuilder(base);
        sb.append("\n\n请基于以下参考资料回答用户问题。\n")
          .append("如果资料中没有相关信息，直接说\"我没有找到相关资料\"，不要编造。\n\n")
          .append("【参考资料】\n");
        int idx = 1;
        for (ChunkHit hit : kept) {
            sb.append("[").append(idx++).append("] ").append(hit.getContent()).append("\n");
        }
        return sb.toString();
    }

    // ════════════════════════════════════════════════════════════════
    // MCP 工具调用
    // ════════════════════════════════════════════════════════════════

    /** Agent 工具绑定信息（从 agent_tool + mcp_tool 联查）。 */
    private record ToolBinding(Long mcpServerId, String toolName, String description,
                               Map<String, Object> inputSchema) {}

    /**
     * 加载 Agent 绑定的全部工具：agent_tool → mcp_tool。
     * 工具列表为空时返回空 list，调用方直接走原有流式逻辑。
     */
    private List<ToolBinding> loadToolBindings(Long agentId) {
        List<AgentTool> bindings = agentToolMapper.selectList(
                new LambdaQueryWrapper<AgentTool>().eq(AgentTool::getAgentId, agentId));
        if (bindings.isEmpty()) return List.of();

        List<ToolBinding> result = new ArrayList<>();
        for (AgentTool at : bindings) {
            List<McpTool> tools = mcpToolMapper.selectList(
                    new LambdaQueryWrapper<McpTool>().eq(McpTool::getMcpServerId, at.getToolId()));
            for (McpTool t : tools) {
                result.add(new ToolBinding(at.getToolId(), t.getName(),
                        t.getDescription() != null ? t.getDescription() : "", t.getInputSchema()));
            }
        }
        return result;
    }

    /** 把 ToolBinding 转成 OpenAI function-calling tools 格式。 */
    private List<Map<String, Object>> buildToolSchemas(List<ToolBinding> bindings) {
        List<Map<String, Object>> tools = new ArrayList<>();
        for (ToolBinding b : bindings) {
            Map<String, Object> func = new LinkedHashMap<>();
            func.put("name", b.toolName);
            func.put("description", b.description);
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("type", "object");
            params.put("properties", b.inputSchema != null
                    ? b.inputSchema.getOrDefault("properties", Map.of()) : Map.of());
            params.put("required", b.inputSchema != null
                    ? b.inputSchema.getOrDefault("required", List.of()) : List.of());
            func.put("parameters", params);

            Map<String, Object> tool = new LinkedHashMap<>();
            tool.put("type", "function");
            tool.put("function", func);
            tools.add(tool);
        }
        return tools;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(Object obj) {
        return obj instanceof Map<?, ?> m ? (Map<String, Object>) m : null;
    }

    /** 用户问题向量化（与文档分块使用同一 EmbeddingClient / 同一模型）。 */
    private float[] embedQuery(String text) {
        return embeddingClient.embed(text);
    }

    /** 日志用：截断长文本到 40 字、去换行，避免刷屏。 */
    private String abbreviate(String s) {
        if (s == null) return "";
        String oneLine = s.replaceAll("\\s+", " ").trim();
        return oneLine.length() > 40 ? oneLine.substring(0, 40) + "…" : oneLine;
    }


    /** 从 PG 查最近 maxTurns 轮历史（正序，最旧在前）。注意：此时用户消息已写入 PG，需排除当前这条。 */
    private List<ChatRequest.Message> loadHistoryFromDb(Long sessionId, int maxTurns) {
        List<ChatMessage> history = chatMessageMapper.selectList(
                new LambdaQueryWrapper<ChatMessage>()
                        .eq(ChatMessage::getSessionId, sessionId)
                        .orderByDesc(ChatMessage::getCreatedAt)
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

package com.hify.modules.chat.service;

import com.hify.modules.chat.dto.MessageResp;
import com.hify.modules.chat.dto.SessionCreateReq;
import com.hify.modules.chat.dto.SessionResp;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

public interface ChatService {

    /** 创建会话。 */
    SessionResp createSession(SessionCreateReq req);

    /** 列出某 Agent 的会话（按最近活跃倒序）。agentId 为 null 时列全部。 */
    List<SessionResp> listSessions(Long agentId);

    /**
     * 游标分页查历史消息（按时间倒序，禁止 LIMIT offset）。
     *
     * @param sessionId 会话 id
     * @param beforeId  游标：上一页最小的消息 id，首页传 null
     * @param size      每页条数
     * @return 消息列表（倒序，最新在前）
     */
    List<MessageResp> listMessages(Long sessionId, Long beforeId, int size);

    /** 删除会话（逻辑删除会话 + 物理删除其消息）。 */
    void deleteSession(Long sessionId);

    /**
     * 发送消息（流式 SSE）。创建并返回 {@link SseEmitter}，在 {@code llmStreamExecutor}
     * 上异步执行 LLM 调用并逐字推送。会话必须已存在（由 {@code POST /sessions} 显式创建）。
     * <p>
     * 异常处理：
     * <ul>
     *   <li>LLM/SSE 超时 → emitter {@code onTimeout} 回调置位停止标志，中止 LLM 读取；</li>
     *   <li>客户端断开（{@code emitter.send} 抛 IOException）→ 置位停止标志中止 LLM 调用，
     *       并对 emitter 调 {@code completeWithError}；</li>
     *   <li>LLM 异常 / 其他异常 → 保存已生成的部分回复后 {@code completeWithError}。</li>
     * </ul>
     * 本方法返回 SseEmitter，<b>不可加 {@code @Transactional}</b>；消息写入委托
     * {@link ChatMessageWriteService} 的独立事务方法，秒级提交，不让 DB 连接跨越 LLM IO。
     *
     * @param sessionId 会话 id（必填，由路径参数定位）
     * @param content   用户消息内容
     * @return SSE 发射器
     */
    SseEmitter sendMessage(Long sessionId, String content);
}

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
     * 流式对话：异步执行 LLM 调用，通过 SseEmitter 逐字推送。
     * 会话必须已存在（由 {@code POST /sessions} 显式创建）。
     *
     * @param sessionId 会话 id（路径参数）
     * @param content   用户消息内容
     * @param emitter   SSE 发射器
     */
    void streamChat(Long sessionId, String content, SseEmitter emitter);
}

package com.hify.modules.chat.dto;

import com.hify.modules.chat.entity.ChatMessage;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 消息响应。
 */
@Data
public class MessageResp {

    private Long id;
    private Long sessionId;
    private String role;
    private String content;
    private Integer tokens;
    private String finishReason;
    private Integer latencyMs;
    private LocalDateTime createdAt;

    public static MessageResp from(ChatMessage m) {
        MessageResp r = new MessageResp();
        r.id = m.getId();
        r.sessionId = m.getSessionId();
        r.role = m.getRole();
        r.content = m.getContent();
        r.tokens = m.getTokens();
        r.finishReason = m.getFinishReason();
        r.latencyMs = m.getLatencyMs();
        r.createdAt = m.getCreatedAt();
        return r;
    }
}

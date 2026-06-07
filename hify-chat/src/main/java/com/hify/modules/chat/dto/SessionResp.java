package com.hify.modules.chat.dto;

import com.hify.modules.chat.entity.ChatSession;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 会话响应。
 */
@Data
public class SessionResp {

    private Long id;
    private Long agentId;
    private String title;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static SessionResp from(ChatSession s) {
        SessionResp r = new SessionResp();
        r.id = s.getId();
        r.agentId = s.getAgentId();
        r.title = s.getTitle();
        r.status = s.getStatus();
        r.createdAt = s.getCreatedAt();
        r.updatedAt = s.getUpdatedAt();
        return r;
    }
}

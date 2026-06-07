package com.hify.modules.knowledge.dto;

import com.hify.modules.knowledge.entity.KnowledgeBase;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 知识库响应。
 */
@Data
public class KnowledgeBaseResp {

    private Long id;
    private String name;
    private String description;
    private Integer enabled;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static KnowledgeBaseResp from(KnowledgeBase kb) {
        KnowledgeBaseResp r = new KnowledgeBaseResp();
        r.id = kb.getId();
        r.name = kb.getName();
        r.description = kb.getDescription();
        r.enabled = kb.getEnabled();
        r.createdAt = kb.getCreatedAt();
        r.updatedAt = kb.getUpdatedAt();
        return r;
    }
}

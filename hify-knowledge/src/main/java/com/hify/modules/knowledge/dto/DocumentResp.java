package com.hify.modules.knowledge.dto;

import com.hify.modules.knowledge.entity.Document;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 文档响应（含状态字段，驱动前端轮询）。
 */
@Data
public class DocumentResp {

    private Long id;
    private Long knowledgeBaseId;
    private String name;
    private String fileType;
    private Long fileSize;
    private String status;
    private String errorMessage;
    private Integer chunkCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static DocumentResp from(Document d) {
        DocumentResp r = new DocumentResp();
        r.id = d.getId();
        r.knowledgeBaseId = d.getKnowledgeBaseId();
        r.name = d.getName();
        r.fileType = d.getFileType();
        r.fileSize = d.getFileSize();
        r.status = d.getStatus();
        r.errorMessage = d.getErrorMessage();
        r.chunkCount = d.getChunkCount();
        r.createdAt = d.getCreatedAt();
        r.updatedAt = d.getUpdatedAt();
        return r;
    }
}

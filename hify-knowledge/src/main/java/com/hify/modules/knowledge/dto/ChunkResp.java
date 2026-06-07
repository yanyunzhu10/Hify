package com.hify.modules.knowledge.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 切片响应（不含 embedding，只返回可读字段）。
 */
@Data
public class ChunkResp {

    private Long id;
    private Long documentId;
    private Integer chunkIndex;
    private String content;
    private Integer tokenCount;
    private LocalDateTime createdAt;
}

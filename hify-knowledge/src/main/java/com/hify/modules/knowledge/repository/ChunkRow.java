package com.hify.modules.knowledge.repository;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * PG t_document_chunk 行（不含 embedding，只返回可读列）。
 */
@Data
public class ChunkRow {

    private Long id;
    private Long documentId;
    private Integer chunkIndex;
    private String content;
    private Integer tokenCount;
    private LocalDateTime createdAt;
}

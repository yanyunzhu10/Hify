package com.hify.modules.knowledge.repository;

import lombok.Data;

/**
 * 向量检索命中结果。
 */
@Data
public class ChunkHit {

    private Long id;
    private Long documentId;
    private Integer chunkIndex;
    private String content;
    private Integer tokenCount;
    /** 余弦距离（越小越相似） */
    private Double distance;
}

package com.hify.modules.knowledge.repository;

import com.pgvector.PGvector;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;

/**
 * t_document_chunk 的 PG/pgvector 数据访问（JdbcTemplate，不走 MyBatis）。
 * <p>
 * 只有两个操作：批量写入（向量化后一批塞进去）和 KNN 相似度检索（cosine <=>）。
 * 用 JdbcTemplate 写裸 SQL 比配 Mapper XML 更直白，也避免了 MyBatis TypeHandler 在
 * 多数据源下的注册麻烦。
 * </p>
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class ChunkRepository {

    @Qualifier("pgvectorJdbcTemplate")
    private final JdbcTemplate tpl;

    // ================================================================
    // 批量写入
    // ================================================================

    /**
     * 批量插入切片向量。单条 SQL，一次 round-trip。
     *
     * @param rows 每行：{documentId, chunkIndex, content, embedding, tokenCount}
     */
    public void batchInsert(List<Object[]> rows) {
        if (rows == null || rows.isEmpty()) return;

        tpl.batchUpdate("""
                INSERT INTO t_document_chunk (document_id, chunk_index, content, embedding, token_count)
                VALUES (?, ?, ?, ?, ?)
                """, rows);
        log.info("批量写入 {} 条向量", rows.size());
    }

    // ================================================================
    // 相似度检索
    // ================================================================

    /**
     * 余弦相似度 KNN 检索。返回 TopK 条切片原文 + 距离。
     *
     * @param queryVec       用户问题的 embedding（float[]）
     * @param knowledgeBaseId 知识库 id
     * @param topK           返回条数
     */
    public List<ChunkHit> searchNearest(float[] queryVec, Long knowledgeBaseId, int topK) {
        // 思路：
        //  1. SET LOCAL ivfflat.probes 控制召回精度
        //  2. <=> 是余弦距离算子（越小越相似），按此排序取 topK
        //  3. JOIN t_document 过滤知识库 + 状态
        tpl.execute("SET LOCAL ivfflat.probes = 10");

        String sql = """
                SELECT c.id, c.document_id, c.chunk_index, c.content, c.token_count,
                       c.embedding <=> ?::vector AS distance
                FROM t_document_chunk c
                JOIN t_document d ON d.id = c.document_id
                WHERE d.knowledge_base_id = ?
                  AND d.status = 'DONE'
                  AND d.deleted = 0
                  AND c.deleted = 0
                ORDER BY c.embedding <=> ?::vector
                LIMIT ?
                """;

        return tpl.query(sql,
                ps -> {
                    // 向量参数必须用 PGobject(PGvector)，不能用 setObject(float[])
                    try {
                        PGvector v = new PGvector(queryVec);
                        ps.setObject(1, v);
                        ps.setLong(2, knowledgeBaseId);
                        ps.setObject(3, v);
                        ps.setInt(4, topK);
                    } catch (SQLException e) {
                        throw new RuntimeException(e);
                    }
                },
                (rs, rowNum) -> {
                    ChunkHit hit = new ChunkHit();
                    hit.setId(rs.getLong("id"));
                    hit.setDocumentId(rs.getLong("document_id"));
                    hit.setChunkIndex(rs.getInt("chunk_index"));
                    hit.setContent(rs.getString("content"));
                    hit.setTokenCount(rs.getInt("token_count"));
                    hit.setDistance(rs.getDouble("distance"));
                    return hit;
                });
    }

    // ================================================================
    // 查询 & 删除（文档管理用）
    // ================================================================

    /**
     * 查询某文档的全部未删除切片（按 chunk_index 正序）。
     */
    public List<ChunkRow> findByDocumentId(Long documentId) {
        return tpl.query("""
                SELECT id, document_id, chunk_index, content, token_count, created_at
                FROM t_document_chunk
                WHERE document_id = ? AND deleted = 0
                ORDER BY chunk_index
                """, (rs, rowNum) -> {
            ChunkRow r = new ChunkRow();
            r.setId(rs.getLong("id"));
            r.setDocumentId(rs.getLong("document_id"));
            r.setChunkIndex(rs.getInt("chunk_index"));
            r.setContent(rs.getString("content"));
            r.setTokenCount(rs.getInt("token_count"));
            r.setCreatedAt(rs.getObject("created_at", LocalDateTime.class));
            return r;
        }, documentId);
    }

    /**
     * 逻辑删除某文档的全部切片。
     */
    public int logicalDeleteByDocumentId(Long documentId) {
        return tpl.update(
                "UPDATE t_document_chunk SET deleted = 1 WHERE document_id = ? AND deleted = 0",
                documentId);
    }
}

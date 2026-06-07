package com.hify.modules.knowledge.service;

import com.hify.modules.knowledge.dto.ChunkResp;
import com.hify.modules.knowledge.dto.DocumentResp;

import java.util.List;

public interface DocumentService {

    /**
     * 上传文档（异步处理）。
     * <p>
     * 1. 校验文件类型与大小<br>
     * 2. 落盘到 upload 目录<br>
     * 3. 写 MySQL 记录（status=PENDING）<br>
     * 4. 提交异步任务到 docProcessExecutor<br>
     * 5. 立即返回 documentId
     * </p>
     *
     * @param kbId     知识库 id
     * @param filename 原始文件名
     * @param content  文件字节
     * @return 文档响应（status=PENDING，前端轮询）
     */
    DocumentResp upload(Long kbId, String filename, byte[] content);

    /** 分页查询知识库下的文档列表。 */
    List<DocumentResp> listByKb(Long kbId, int page, int size);

    /** 查询单个文档详情。 */
    DocumentResp getById(Long id);

    /** 查询文档的切片列表（PG）。 */
    List<ChunkResp> listChunks(Long documentId);

    /** 逻辑删除文档 + PG 里的 chunk。 */
    void delete(Long id);
}

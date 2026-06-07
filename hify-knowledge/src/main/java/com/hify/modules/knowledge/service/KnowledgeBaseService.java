package com.hify.modules.knowledge.service;

import com.hify.modules.knowledge.dto.KnowledgeBaseCreateReq;
import com.hify.modules.knowledge.dto.KnowledgeBaseResp;
import com.hify.modules.knowledge.dto.KnowledgeBaseUpdateReq;

import java.util.List;

public interface KnowledgeBaseService {

    /** 创建知识库。name 唯一（区分 normal 逻辑删除）。 */
    KnowledgeBaseResp create(KnowledgeBaseCreateReq req);

    /** 分页查询知识库列表（name 模糊搜索）。 */
    List<KnowledgeBaseResp> list(int page, int size, String name);

    /** 查询单个知识库详情。 */
    KnowledgeBaseResp getById(Long id);

    /** 更新知识库。 */
    KnowledgeBaseResp update(Long id, KnowledgeBaseUpdateReq req);

    /** 逻辑删除知识库 + 关联的 document + document_chunk（PG）。 */
    void delete(Long id);
}

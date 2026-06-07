package com.hify.modules.knowledge.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hify.common.exception.BizException;
import com.hify.common.exception.ErrorCode;
import com.hify.modules.knowledge.dto.KnowledgeBaseCreateReq;
import com.hify.modules.knowledge.dto.KnowledgeBaseResp;
import com.hify.modules.knowledge.dto.KnowledgeBaseUpdateReq;
import com.hify.modules.knowledge.entity.Document;
import com.hify.modules.knowledge.entity.KnowledgeBase;
import com.hify.modules.knowledge.mapper.DocumentMapper;
import com.hify.modules.knowledge.mapper.KnowledgeBaseMapper;
import com.hify.modules.knowledge.service.KnowledgeBaseService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeBaseServiceImpl implements KnowledgeBaseService {

    private final KnowledgeBaseMapper kbMapper;
    private final DocumentMapper docMapper;

    /** 指向 PgVectorConfig 中创建的 pgvectorJdbcTemplate bean（名称与字段同名，Spring 构造函数注入按名称匹配）。 */
    private final JdbcTemplate pgvectorJdbcTemplate;

    @Override
    @Transactional
    public KnowledgeBaseResp create(KnowledgeBaseCreateReq req) {
        assertNameUnique(req.getName(), null);

        KnowledgeBase kb = new KnowledgeBase();
        kb.setName(req.getName());
        kb.setDescription(req.getDescription() != null ? req.getDescription() : "");
        kb.setEnabled(1);
        kbMapper.insert(kb);
        log.info("知识库创建 id={} name={}", kb.getId(), kb.getName());
        return KnowledgeBaseResp.from(kb);
    }

    @Override
    public List<KnowledgeBaseResp> list(int page, int size, String name) {
        LambdaQueryWrapper<KnowledgeBase> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(name)) {
            wrapper.like(KnowledgeBase::getName, name);
        }
        wrapper.orderByDesc(KnowledgeBase::getCreatedAt);
        Page<KnowledgeBase> result = kbMapper.selectPage(new Page<>(page, size), wrapper);
        return result.getRecords().stream().map(KnowledgeBaseResp::from).toList();
    }

    @Override
    public KnowledgeBaseResp getById(Long id) {
        return KnowledgeBaseResp.from(requireKb(id));
    }

    @Override
    @Transactional
    public KnowledgeBaseResp update(Long id, KnowledgeBaseUpdateReq req) {
        KnowledgeBase kb = requireKb(id);

        if (req.getName() != null) {
            assertNameUnique(req.getName(), id);
            kb.setName(req.getName());
        }
        if (req.getDescription() != null) {
            kb.setDescription(req.getDescription());
        }
        if (req.getEnabled() != null) {
            kb.setEnabled(req.getEnabled());
        }
        kbMapper.updateById(kb);
        log.info("知识库更新 id={}", id);
        return KnowledgeBaseResp.from(kb);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        KnowledgeBase kb = requireKb(id);

        // 1) 逻辑删除关联的全部 document（MyBatis-Plus logic-delete 自动标记 deleted=1）
        docMapper.delete(new LambdaQueryWrapper<Document>()
                .eq(Document::getKnowledgeBaseId, id));

        // 2) 逻辑删除 document_chunk（PG 侧，用简单 UPDATE）
        pgvectorJdbcTemplate.update("UPDATE t_document_chunk SET deleted = 1 "
                + "WHERE document_id IN (SELECT id FROM t_document WHERE knowledge_base_id = ?)", id);

        // 3) 逻辑删除知识库自身
        kbMapper.deleteById(id);
        log.info("知识库删除 id={}（关联 document + chunk 已级联标记删除）", id);
    }

    // ============ 校验 ============

    private KnowledgeBase requireKb(Long id) {
        KnowledgeBase kb = kbMapper.selectById(id);
        if (kb == null) {
            throw new BizException(ErrorCode.KNOWLEDGE_BASE_NOT_FOUND);
        }
        return kb;
    }

    /** 名称在未删除的记录中必须唯一。 */
    private void assertNameUnique(String name, Long excludeId) {
        LambdaQueryWrapper<KnowledgeBase> wrapper = new LambdaQueryWrapper<KnowledgeBase>()
                .eq(KnowledgeBase::getName, name);
        if (excludeId != null) {
            wrapper.ne(KnowledgeBase::getId, excludeId);
        }
        if (kbMapper.selectCount(wrapper) > 0) {
            throw new BizException(ErrorCode.KNOWLEDGE_BASE_NAME_EXISTS);
        }
    }
}

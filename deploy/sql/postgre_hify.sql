-- ----------------------------
DROP TABLE IF EXISTS `t_document_chunk`;
CREATE TABLE `t_document_chunk` (
`id`          bigint       NOT NULL AUTO_INCREMENT COMMENT '主键',
`document_id` bigint       NOT NULL COMMENT '关联 t_document.id',
`chunk_index` int          NOT NULL COMMENT '切片序号，从 0 开始',
`content`     text         NOT NULL COMMENT '切片原文',
`token_count` int          NOT NULL DEFAULT 0 COMMENT '估算 token 数',
`deleted`     smallint     NOT NULL DEFAULT 0 COMMENT '逻辑删除',
`created_at`  timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
PRIMARY KEY (`id`),
KEY `idx_chunk_document` (`document_id`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='文档切片（向量列由 pgvector 扩展单独添加）';

CREATE INDEX idx_chunk_kb ON document_chunk (knowledge_base_id) WHERE deleted = 0;
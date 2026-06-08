/*
Navicat Premium Dump SQL

Source Server         : MySQL
Source Server Type    : MySQL
Source Server Version : 8.0+
File Encoding         : utf8mb4

Date: 07/06/2026
*/

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- Table structure for t_agent
-- ----------------------------
DROP TABLE IF EXISTS `t_agent`;
CREATE TABLE `t_agent` (
`id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
`name` varchar(100) NOT NULL COMMENT 'Agent名称',
`description` varchar(500) NOT NULL DEFAULT '' COMMENT '描述',
`system_prompt` text NOT NULL COMMENT '角色指令，可以很长',
`model_config_id` bigint NOT NULL COMMENT '绑定的模型配置',
`knowledge_base_id` bigint DEFAULT NULL COMMENT '关联知识库 ID（可空，未绑定知识库）',
`temperature` decimal(3,2) NOT NULL DEFAULT 0.70 COMMENT '0.00~1.00',
`max_tokens` int NOT NULL DEFAULT 2048 COMMENT '最大token数',
`max_context_turns` int NOT NULL DEFAULT 10 COMMENT '保留最近几轮上下文',
`enabled` smallint NOT NULL DEFAULT 1 COMMENT '0不可用，1可用',
`created_at` timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
`updated_at` timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间',
`deleted` smallint NOT NULL DEFAULT 0 COMMENT '1 删除',
PRIMARY KEY (`id`),
UNIQUE KEY `agent_name_key` (`name`),
KEY `idx_agent_model_config_id` (`model_config_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Agent 配置';

-- ----------------------------
-- Table structure for t_agent_tool
-- ----------------------------
DROP TABLE IF EXISTS `t_agent_tool`;
CREATE TABLE `t_agent_tool` (
`id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
`agent_id` bigint NOT NULL COMMENT 'Agent ID',
`tool_id` bigint NOT NULL COMMENT '关联 mcp_server.id',
`created_at` timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
PRIMARY KEY (`id`),
UNIQUE KEY `uk_agent_tool` (`agent_id`,`tool_id`),
KEY `idx_agent_tool_agent_id` (`agent_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Agent 与工具关联';

-- ----------------------------
-- Table structure for t_chat_message
-- ----------------------------
DROP TABLE IF EXISTS `t_chat_message`;
CREATE TABLE `t_chat_message` (
`id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
`session_id` bigint NOT NULL COMMENT '关联 t_chat_session.id',
`role` varchar(20) NOT NULL COMMENT '角色：user | assistant | system',
`content` text NOT NULL COMMENT '消息内容',
`tokens` int NOT NULL DEFAULT 0 COMMENT '本条消息消耗的 tokens',
`finish_reason` varchar(20) DEFAULT NULL COMMENT '结束原因：stop | length | error',
`latency_ms` int DEFAULT NULL COMMENT '响应耗时 ms（assistant 消息记录）',
`created_at` timestamp(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
`updated_at` timestamp(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
`deleted` smallint NOT NULL DEFAULT 0 COMMENT '删除标记',
PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='对话消息（游标分页，禁止 LIMIT offset）';

-- ----------------------------
-- Table structure for t_chat_session
-- ----------------------------
DROP TABLE IF EXISTS `t_chat_session`;
CREATE TABLE `t_chat_session` (
`id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
`agent_id` bigint NOT NULL COMMENT '关联 t_agent.id',
`title` varchar(128) NOT NULL DEFAULT '新对话' COMMENT '会话标题',
`summary` varchar(512) NOT NULL DEFAULT '' COMMENT '摘要（首条消息自动截取）',
`created_at` timestamp(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
`updated_at` timestamp(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
`deleted` int NOT NULL DEFAULT 0 COMMENT '删除标记',
`status` varchar(20) NOT NULL COMMENT 'ACTIVE / ARCHIVED',
PRIMARY KEY (`id`),
KEY `idx_chat_session_agent_deleted_updated` (`agent_id`,`deleted`,`updated_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='对话会话';

-- ----------------------------
-- Table structure for t_demo_item
-- ----------------------------
DROP TABLE IF EXISTS `t_demo_item`;
CREATE TABLE `t_demo_item` (
`id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
`name` varchar(64) NOT NULL COMMENT '名称',
`status` int NOT NULL DEFAULT 0 COMMENT '状态',
`created_at` timestamp(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
`updated_at` timestamp(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
`deleted` tinyint(1) NOT NULL DEFAULT 0 COMMENT '删除标记',
PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='CRUD 演示表';

-- ----------------------------
-- Table structure for t_mcp_server
-- ----------------------------
DROP TABLE IF EXISTS `t_mcp_server`;
CREATE TABLE `t_mcp_server` (
`id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
`name` varchar(64) NOT NULL COMMENT 'MCP 服务名称',
`description` varchar(256) NOT NULL DEFAULT '' COMMENT '用途描述',
`type` varchar(16) NOT NULL COMMENT '连接类型：stdio | sse | http',
`command` varchar(256) NOT NULL DEFAULT '' COMMENT 'stdio：启动命令，如 uvx mcp-server-fetch',
`url` varchar(256) NOT NULL DEFAULT '' COMMENT 'sse/http：服务地址',
`args` varchar(1024) NOT NULL DEFAULT '[]' COMMENT 'JSON 数组：命令行参数',
`env_vars` varchar(2048) NOT NULL DEFAULT '{}' COMMENT 'JSON 对象：注入的环境变量',
`enabled` tinyint(1) NOT NULL DEFAULT 1 COMMENT '启用状态',
`created_at` timestamp(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
`updated_at` timestamp(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
`deleted` tinyint(1) NOT NULL DEFAULT 0 COMMENT '删除标记',
PRIMARY KEY (`id`),
KEY `idx_mcp_server_type_deleted` (`type`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='MCP 工具服务配置';

-- ----------------------------
-- Table structure for t_model_config
-- ----------------------------
DROP TABLE IF EXISTS `t_model_config`;
CREATE TABLE `t_model_config` (
`id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
`provider_id` bigint NOT NULL COMMENT '所属供应商 ID',
`name` varchar(100) NOT NULL COMMENT '展示名，如 GPT-4o',
`model_id` varchar(100) NOT NULL COMMENT '调用时传给 API 的值',
`context_size` int DEFAULT NULL COMMENT '上下文窗口大小（token 数）',
`extra_params` json DEFAULT NULL COMMENT '模型级别扩展参数',
`enabled` int NOT NULL DEFAULT 1 COMMENT '0=禁用 1=启用',
`created_at` timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
`updated_at` timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间',
`deleted` int NOT NULL DEFAULT 0 COMMENT '删除标记',
PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='模型配置';

-- ----------------------------
-- Table structure for t_provider
-- ----------------------------
DROP TABLE IF EXISTS `t_provider`;
CREATE TABLE `t_provider` (
`id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
`name` varchar(100) NOT NULL COMMENT '供应商名称，唯一',
`type` varchar(30) NOT NULL COMMENT 'OPENAI/ANTHROPIC/OLLAMA/OPENAI_COMPATIBLE',
`base_url` varchar(500) NOT NULL COMMENT 'API 基础地址',
`auth_config` json DEFAULT NULL COMMENT '鉴权配置，结构按 type 不同',
`enabled` int NOT NULL DEFAULT 1 COMMENT '0=禁用 1=启用',
`created_at` timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
`updated_at` timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间',
`deleted` smallint NOT NULL DEFAULT 0 COMMENT '1 删除， 0存在',
PRIMARY KEY (`id`),
UNIQUE KEY `idx_provider_name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='模型提供商';

-- ----------------------------
-- Table structure for t_provider_health
-- ----------------------------
DROP TABLE IF EXISTS `t_provider_health`;
CREATE TABLE `t_provider_health` (
 `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
 `provider_id` bigint NOT NULL COMMENT '供应商 ID，唯一索引',
 `status` varchar(20) DEFAULT 'UNKNOWN' COMMENT 'UP/DOWN/DEGRADED/UNKNOWN',
 `last_check_at` timestamp(6) NULL DEFAULT NULL COMMENT '最后探测时间',
 `last_success_at` timestamp(6) NULL DEFAULT NULL COMMENT '最后成功时间',
 `fail_count` int DEFAULT 0 COMMENT '连续失败次数',
 `latency_ms` int DEFAULT NULL COMMENT '最近一次延迟 ms',
 `error_message` varchar(500) DEFAULT NULL COMMENT '最近失败原因',
 `updated_at` timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间',
 PRIMARY KEY (`id`),
 UNIQUE KEY `idx_provider_health_provider_id` (`provider_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='供应商健康状态';

-- ----------------------------
-- Table structure for t_knowledge_base
-- ----------------------------
DROP TABLE IF EXISTS `t_knowledge_base`;
CREATE TABLE `t_knowledge_base` (
  `id`          bigint      NOT NULL AUTO_INCREMENT COMMENT '主键',
  `name`        varchar(100) NOT NULL COMMENT '知识库名称',
  `description` varchar(500) NOT NULL DEFAULT '' COMMENT '描述',
  `enabled`     smallint    NOT NULL DEFAULT 1 COMMENT '是否启用：0=否 1=是',
  `deleted`     smallint    NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  `created_at`  timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
  `updated_at`  timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '更新时间',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='知识库';

-- ----------------------------
-- Table structure for t_document
-- ----------------------------
DROP TABLE IF EXISTS `t_document`;
CREATE TABLE `t_document` (
  `id`                bigint       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `knowledge_base_id` bigint       NOT NULL COMMENT '所属知识库 ID',
  `name`              varchar(200) NOT NULL COMMENT '文档名称',
  `file_type`         varchar(20)  NOT NULL COMMENT '文件类型：txt / pdf / md',
  `file_size`         bigint       NOT NULL COMMENT '文件大小（字节）',
  `status`            varchar(20)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING → PROCESSING → DONE / FAILED',
  `error_message`     varchar(500) DEFAULT NULL COMMENT '失败原因',
  `chunk_count`       int          NOT NULL DEFAULT 0 COMMENT '切片数量',
  `deleted`           smallint     NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  `created_at`        timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
  `updated_at`        timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_document_kb_status` (`knowledge_base_id`, `deleted`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='知识库文档';

SET FOREIGN_KEY_CHECKS = 1;
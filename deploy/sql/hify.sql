/*
 Navicat Premium Dump SQL

 Source Server         : localhost
 Source Server Type    : MySQL
 Source Server Version : 80409 (8.4.9)
 Source Host           : localhost:3306
 Source Schema         : hify

 Target Server Type    : MySQL
 Target Server Version : 80409 (8.4.9)
 File Encoding         : 65001

 Date: 30/05/2026 17:11:22
*/

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- Table structure for t_agent
-- ----------------------------
DROP TABLE IF EXISTS `t_agent`;
CREATE TABLE `t_agent` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `name` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'Agent 名称',
  `description` varchar(256) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT 'Agent 用途描述',
  `system_prompt` mediumtext COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '系统提示词',
  `model_config_id` bigint NOT NULL COMMENT '绑定的模型配置 ID',
  `enabled` tinyint(1) NOT NULL DEFAULT '1',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `deleted` tinyint(1) NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_name_deleted` (`name`,`deleted`),
  KEY `idx_model_config_deleted` (`model_config_id`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Agent 配置';

-- ----------------------------
-- Table structure for t_agent_tool
-- ----------------------------
DROP TABLE IF EXISTS `t_agent_tool`;
CREATE TABLE `t_agent_tool` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `agent_id` bigint NOT NULL COMMENT '关联 t_agent.id',
  `mcp_server_id` bigint NOT NULL COMMENT '关联 t_mcp_server.id',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `deleted` tinyint(1) NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_agent_deleted` (`agent_id`,`deleted`),
  KEY `idx_mcp_server_deleted` (`mcp_server_id`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Agent 绑定的 MCP 工具（多对多）';

-- ----------------------------
-- Table structure for t_chat_message
-- ----------------------------
DROP TABLE IF EXISTS `t_chat_message`;
CREATE TABLE `t_chat_message` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `session_id` bigint NOT NULL COMMENT '关联 t_chat_session.id',
  `role` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '角色：user | assistant | tool',
  `content` mediumtext COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '消息内容',
  `tokens` int NOT NULL DEFAULT '0' COMMENT '本条消息消耗的 tokens',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `deleted` tinyint(1) NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_session_deleted_created` (`session_id`,`deleted`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='对话消息（游标分页，禁止 LIMIT offset）';

-- ----------------------------
-- Table structure for t_chat_session
-- ----------------------------
DROP TABLE IF EXISTS `t_chat_session`;
CREATE TABLE `t_chat_session` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `agent_id` bigint NOT NULL COMMENT '关联 t_agent.id',
  `title` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '新对话' COMMENT '会话标题',
  `summary` varchar(512) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT '摘要（首条消息自动截取）',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `deleted` tinyint(1) NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_agent_deleted_updated` (`agent_id`,`deleted`,`updated_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='对话会话';

-- ----------------------------
-- Table structure for t_demo_item
-- ----------------------------
DROP TABLE IF EXISTS `t_demo_item`;
CREATE TABLE `t_demo_item` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `name` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '名称',
  `status` int NOT NULL DEFAULT '0' COMMENT '状态',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `deleted` tinyint(1) NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='CRUD 演示表';

-- ----------------------------
-- Table structure for t_mcp_server
-- ----------------------------
DROP TABLE IF EXISTS `t_mcp_server`;
CREATE TABLE `t_mcp_server` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `name` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'MCP 服务名称',
  `description` varchar(256) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT '用途描述',
  `type` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '连接类型：stdio | sse | http',
  `command` varchar(256) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT 'stdio：启动命令，如 uvx mcp-server-fetch',
  `url` varchar(256) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT 'sse/http：服务地址',
  `args` varchar(1024) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '[]' COMMENT 'JSON 数组：命令行参数',
  `env_vars` varchar(2048) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '{}' COMMENT 'JSON 对象：注入的环境变量',
  `enabled` tinyint(1) NOT NULL DEFAULT '1',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `deleted` tinyint(1) NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_type_deleted` (`type`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='MCP 工具服务配置';

-- ----------------------------
-- Table structure for t_model_config
-- ----------------------------
DROP TABLE IF EXISTS `t_model_config`;
CREATE TABLE `t_model_config` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `provider_id` bigint NOT NULL COMMENT '所属供应商 ID',
  `name` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '展示名，如 GPT-4o',
  `model_id` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '调用时传给 API 的值',
  `context_size` int DEFAULT NULL COMMENT '上下文窗口大小（token 数）',
  `extra_params` json DEFAULT NULL COMMENT '模型级别扩展参数',
  `enabled` tinyint DEFAULT '1' COMMENT '0=禁用 1=启用',
  `created_at` datetime NOT NULL,
  `updated_at` datetime NOT NULL,
  `deleted` tinyint DEFAULT '0',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='模型配置';

-- ----------------------------
-- Table structure for t_provider
-- ----------------------------
DROP TABLE IF EXISTS `t_provider`;
CREATE TABLE `t_provider` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '供应商名称，唯一',
  `type` varchar(30) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'OPENAI/ANTHROPIC/OLLAMA/OPENAI_COMPATIBLE',
  `base_url` varchar(500) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'API 基础地址',
  `auth_config` json DEFAULT NULL COMMENT '鉴权配置，结构按 type 不同',
  `enabled` tinyint DEFAULT '1' COMMENT '0=禁用 1=启用',
  `created_at` datetime NOT NULL,
  `updated_at` datetime NOT NULL,
  `deleted` tinyint DEFAULT '0',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='模型提供商';

-- ----------------------------
-- Table structure for t_provider_health
-- ----------------------------
DROP TABLE IF EXISTS `t_provider_health`;
CREATE TABLE `t_provider_health` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `provider_id` bigint NOT NULL COMMENT '供应商 ID，唯一索引',
  `status` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT 'UNKNOWN' COMMENT 'UP/DOWN/DEGRADED/UNKNOWN',
  `last_check_at` datetime DEFAULT NULL COMMENT '最后探测时间',
  `last_success_at` datetime DEFAULT NULL COMMENT '最后成功时间',
  `fail_count` int DEFAULT '0' COMMENT '连续失败次数',
  `latency_ms` int DEFAULT NULL COMMENT '最近一次延迟 ms',
  `error_message` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '最近失败原因',
  `updated_at` datetime NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `idx_provider_health_provider_id` (`provider_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='供应商健康状态';

SET FOREIGN_KEY_CHECKS = 1;

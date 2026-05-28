-- ============================================================
-- Hify 业务表 DDL
-- 规范：t_ 前缀、BIGINT 自增主键、DATETIME(3) 时间、逻辑删除
-- 字符集：utf8mb4 / utf8mb4_unicode_ci
-- 索引原则：低区分度字段（deleted/status）不单独建索引，
--           必须与高区分度字段组合，等值字段在左、范围字段在右
-- ============================================================

-- ------------------------------------------------------------
-- 1. t_provider  LLM 提供商配置
--    每个 provider 代表一个接入端（如 OpenAI 账号、本地 Ollama）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `t_provider` (
    `id`         BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `name`       VARCHAR(64)  NOT NULL                COMMENT '提供商显示名称，如 My-OpenAI',
    `type`       VARCHAR(16)  NOT NULL                COMMENT '类型：openai | claude | gemini | ollama',
    `base_url`   VARCHAR(256) NOT NULL                COMMENT 'API Base URL',
    `api_key`    VARCHAR(256) NOT NULL DEFAULT ''     COMMENT 'API Key（本地部署可留空）',
    `enabled`    TINYINT(1)   NOT NULL DEFAULT 1      COMMENT '是否启用',
    `created_at` DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at` DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    `deleted`    TINYINT(1)   NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    INDEX `idx_type_deleted` (`type`, `deleted`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = 'LLM 提供商配置';


-- ------------------------------------------------------------
-- 2. t_model_config  模型参数配置
--    同一个 provider 下可挂多个模型（gpt-4o / gpt-4o-mini 等）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `t_model_config` (
    `id`             BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `provider_id`    BIGINT        NOT NULL               COMMENT '关联 t_provider.id',
    `name`           VARCHAR(64)   NOT NULL               COMMENT '模型显示名称',
    `model_id`       VARCHAR(64)   NOT NULL               COMMENT '实际模型标识符，如 gpt-4o',
    `context_window` INT           NOT NULL DEFAULT 8192  COMMENT '上下文窗口（tokens）',
    `max_tokens`     INT           NOT NULL DEFAULT 2048  COMMENT '最大输出 tokens',
    `temperature`    DECIMAL(3, 2) NOT NULL DEFAULT 0.70  COMMENT '采样温度 0.00~2.00',
    `enabled`        TINYINT(1)    NOT NULL DEFAULT 1,
    `created_at`     DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at`     DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    `deleted`        TINYINT(1)    NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    INDEX `idx_provider_deleted` (`provider_id`, `deleted`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = '模型参数配置';


-- ------------------------------------------------------------
-- 3. t_mcp_server  MCP 工具服务配置
--    先于 t_agent 建，因为 t_agent_tool 同时引用两者
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `t_mcp_server` (
    `id`          BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `name`        VARCHAR(64)   NOT NULL               COMMENT 'MCP 服务名称',
    `description` VARCHAR(256)  NOT NULL DEFAULT ''    COMMENT '用途描述',
    `type`        VARCHAR(16)   NOT NULL               COMMENT '连接类型：stdio | sse | http',
    `command`     VARCHAR(256)  NOT NULL DEFAULT ''    COMMENT 'stdio：启动命令，如 uvx mcp-server-fetch',
    `url`         VARCHAR(256)  NOT NULL DEFAULT ''    COMMENT 'sse/http：服务地址',
    `args`        VARCHAR(1024) NOT NULL DEFAULT '[]'  COMMENT 'JSON 数组：命令行参数',
    `env_vars`    VARCHAR(2048) NOT NULL DEFAULT '{}'  COMMENT 'JSON 对象：注入的环境变量',
    `enabled`     TINYINT(1)    NOT NULL DEFAULT 1,
    `created_at`  DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at`  DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    `deleted`     TINYINT(1)    NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    INDEX `idx_type_deleted` (`type`, `deleted`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = 'MCP 工具服务配置';


-- ------------------------------------------------------------
-- 4. t_agent  Agent 配置
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `t_agent` (
    `id`              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `name`            VARCHAR(64)  NOT NULL               COMMENT 'Agent 名称',
    `description`     VARCHAR(256) NOT NULL DEFAULT ''    COMMENT 'Agent 用途描述',
    `system_prompt`   MEDIUMTEXT   NOT NULL               COMMENT '系统提示词',
    `model_config_id` BIGINT       NOT NULL               COMMENT '绑定的模型配置 ID',
    `enabled`         TINYINT(1)   NOT NULL DEFAULT 1,
    `created_at`      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at`      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    `deleted`         TINYINT(1)   NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    INDEX `idx_name_deleted`         (`name`, `deleted`),
    INDEX `idx_model_config_deleted` (`model_config_id`, `deleted`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = 'Agent 配置';


-- ------------------------------------------------------------
-- 5. t_agent_tool  Agent ↔ MCP 服务关联（多对多）
--    逻辑删除场景下不加 UNIQUE 约束，由应用层保证不重复绑定
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `t_agent_tool` (
    `id`            BIGINT     NOT NULL AUTO_INCREMENT COMMENT '主键',
    `agent_id`      BIGINT     NOT NULL               COMMENT '关联 t_agent.id',
    `mcp_server_id` BIGINT     NOT NULL               COMMENT '关联 t_mcp_server.id',
    `created_at`    DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at`    DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    `deleted`       TINYINT(1)  NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    INDEX `idx_agent_deleted`      (`agent_id`, `deleted`),
    INDEX `idx_mcp_server_deleted` (`mcp_server_id`, `deleted`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = 'Agent 绑定的 MCP 工具（多对多）';


-- ------------------------------------------------------------
-- 6. t_chat_session  对话会话
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `t_chat_session` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `agent_id`    BIGINT       NOT NULL               COMMENT '关联 t_agent.id',
    `title`       VARCHAR(128) NOT NULL DEFAULT '新对话' COMMENT '会话标题',
    `summary`     VARCHAR(512) NOT NULL DEFAULT ''    COMMENT '摘要（首条消息自动截取）',
    `created_at`  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at`  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    `deleted`     TINYINT(1)   NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    -- 按 agent 查会话列表，按 updated_at 倒序展示最近活跃
    INDEX `idx_agent_deleted_updated` (`agent_id`, `deleted`, `updated_at`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = '对话会话';


-- ------------------------------------------------------------
-- 7. t_chat_message  对话消息（高频写入，潜在大表）
--    分页查询使用游标分页，禁止 LIMIT offset 深分页：
--    WHERE session_id = ?
--      AND deleted = 0
--      AND (created_at < ? OR (created_at = ? AND id < ?))
--    ORDER BY created_at DESC, id DESC
--    LIMIT 20
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `t_chat_message` (
    `id`         BIGINT     NOT NULL AUTO_INCREMENT  COMMENT '主键',
    `session_id` BIGINT     NOT NULL                 COMMENT '关联 t_chat_session.id',
    `role`       VARCHAR(16) NOT NULL                COMMENT '角色：user | assistant | tool',
    `content`    MEDIUMTEXT  NOT NULL                COMMENT '消息内容',
    `tokens`     INT         NOT NULL DEFAULT 0      COMMENT '本条消息消耗的 tokens',
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    `deleted`    TINYINT(1)  NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    -- 游标分页覆盖索引：session_id 等值在左，deleted 次之，created_at 范围在右
    INDEX `idx_session_deleted_created` (`session_id`, `deleted`, `created_at`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = '对话消息（游标分页，禁止 LIMIT offset）';

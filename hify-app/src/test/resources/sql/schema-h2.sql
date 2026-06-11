-- H2 兼容的 Provider 表结构（测试用）
CREATE TABLE IF NOT EXISTS t_provider (
    id          BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    name        VARCHAR(100) NOT NULL,
    type        VARCHAR(50)  NOT NULL,
    base_url    VARCHAR(500) NOT NULL,
    auth_config TEXT         NOT NULL,
    enabled     SMALLINT     NOT NULL DEFAULT 1,
    created_at  TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at  TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    deleted     SMALLINT     NOT NULL DEFAULT 0
);

-- Agent 表结构（测试用）
CREATE TABLE IF NOT EXISTS t_agent (
    id             BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    name           VARCHAR(100) NOT NULL,
    system_prompt  TEXT         NOT NULL,
    model_config_id BIGINT       NOT NULL,
    enabled        SMALLINT     NOT NULL DEFAULT 1,
    created_at     TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at     TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    deleted        SMALLINT     NOT NULL DEFAULT 0
);

-- ModelConfig 表结构（测试用）
CREATE TABLE IF NOT EXISTS t_model_config (
    id            BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    provider_id   BIGINT       NOT NULL,
    model_name    VARCHAR(100) NOT NULL,
    model_type    VARCHAR(50)  NOT NULL,
    max_tokens    INT          NOT NULL,
    enabled       SMALLINT     NOT NULL DEFAULT 1,
    created_at    TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at    TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    deleted       SMALLINT     NOT NULL DEFAULT 0
);

-- ChatSession 表结构（测试用）
CREATE TABLE IF NOT EXISTS t_chat_session (
    id        BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    agent_id  BIGINT       NOT NULL,
    title     VARCHAR(255) NOT NULL,
    status    VARCHAR(50)  NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    deleted   SMALLINT     NOT NULL DEFAULT 0
);

-- ChatMessage 表结构（测试用）
CREATE TABLE IF NOT EXISTS t_chat_message (
    id            BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    session_id    BIGINT       NOT NULL,
    role          VARCHAR(20)  NOT NULL,
    content       TEXT         NOT NULL,
    tokens        INT          DEFAULT NULL,
    finish_reason VARCHAR(50)  DEFAULT NULL,
    latency_ms    INT          DEFAULT NULL,
    created_at    TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at    TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    deleted       SMALLINT     NOT NULL DEFAULT 0
);

-- 外键索引
CREATE INDEX IF NOT EXISTS idx_agent_model_config ON t_agent (model_config_id);
CREATE INDEX IF NOT EXISTS idx_config_provider ON t_model_config (provider_id);
CREATE INDEX IF NOT EXISTS idx_session_agent ON t_chat_session (agent_id);
CREATE INDEX IF NOT EXISTS idx_message_session ON t_chat_message (session_id);
-- Chat 会话和消息测试数据（普通问答场景）

DELETE FROM t_chat_message;
DELETE FROM t_chat_session;
DELETE FROM t_agent_tool;
DELETE FROM t_mcp_tool;
DELETE FROM t_mcp_server;
DELETE FROM t_agent;
DELETE FROM t_model_config;
DELETE FROM t_provider;

-- 1. 插入 Provider
INSERT INTO t_provider (id, name, type, base_url, auth_config, enabled, created_at, updated_at, deleted)
VALUES (1, 'mock-openai', 'MOCK', 'https://api.openai.com/v1', '{"apiKey":"sk-test"}', 1, NOW(), NOW(), 0);

-- 2. 插入 ModelConfig
INSERT INTO t_model_config (id, provider_id, name, model_id, model_name, model_type, max_tokens, enabled, created_at, updated_at, deleted)
VALUES (1, 1, 'gpt-3.5-turbo', 'gpt-3.5-turbo', 'gpt-3.5-turbo', 'CHAT', 4096, 1, NOW(), NOW(), 0);

-- 3. 插入 Agent
INSERT INTO t_agent (id, name, system_prompt, model_config_id, max_context_turns, enabled, created_at, updated_at, deleted)
VALUES (1, 'test-agent', '你是一个友好的助手', 1, 10, 1, NOW(), NOW(), 0);

-- 4. 插入 ChatSession
INSERT INTO t_chat_session (id, agent_id, title, status, created_at, updated_at, deleted)
VALUES (1, 1, '测试会话', 'active', NOW(), NOW(), 0);

-- 注意：不插入消息，测试中会自动创建
-- user 消息：通过 POST /sessions/{id}/messages 接口创建
-- assistant 消息：由 LLM 响应异步创建
-- Provider 表初始化数据（DELETE / GET 测试用）
INSERT INTO t_provider (id, name, type, base_url, auth_config, enabled, created_at, updated_at, deleted)
VALUES
(1, 'existing-openai', 'OPENAI', 'https://api.openai.com/v1', '{"apiKey":"sk-test"}', 1, NOW(), NOW(), 0);
-- Function Calling 测试数据：Agent 绑定 MCP Server 工具

-- 1. 插入 Provider
INSERT INTO t_provider (id, name, type, base_url, auth_config, enabled, created_at, updated_at, deleted)
VALUES (1, 'mock-openai', 'OPENAI', 'https://api.openai.com/v1', '{"apiKey":"sk-test"}', 1, NOW(), NOW(), 0);

-- 2. 插入 ModelConfig
INSERT INTO t_model_config (id, provider_id, model_name, model_type, max_tokens, enabled, created_at, updated_at, deleted)
VALUES (1, 1, 'gpt-3.5-turbo', 'CHAT', 4096, 1, NOW(), NOW(), 0);

-- 3. 插入 MCP Server
INSERT INTO t_mcp_server (id, name, base_url, enabled, created_at, updated_at, deleted)
VALUES (1, 'mock-refund-server', 'http://localhost:8081/mcp', 1, NOW(), NOW(), 0);

-- 4. 插入 MCP Tool
INSERT INTO t_mcp_tool (id, mcp_server_id, name, description, input_schema, enabled, created_at, updated_at, deleted)
VALUES (1, 1, 'check_refund_eligibility', '检查订单是否符合退款条件',
        '{"type":"object","properties":{"orderId":{"type":"string","description":"订单ID"}},"required":["orderId"]}',
        1, NOW(), NOW(), 0);

-- 5. 插入 Agent
INSERT INTO t_agent (id, name, system_prompt, model_config_id, enabled, created_at, updated_at, deleted)
VALUES (1, 'refund-agent', '你是一个退款助手，可以使用工具查询订单的退款资格。', 1, 1, NOW(), NOW(), 0);

-- 6. 插入 AgentTool 关联（Agent 绑定 MCP 工具）
INSERT INTO t_agent_tool (id, agent_id, tool_id, created_at, updated_at, deleted)
VALUES (1, 1, 1, NOW(), NOW(), 0);

-- 7. 插入 ChatSession
INSERT INTO t_chat_session (id, agent_id, title, status, created_at, updated_at, deleted)
VALUES (1, 1, '退款咨询会话', 'active', NOW(), NOW(), 0);

-- 注意：
-- - user 消息：通过 POST /sessions/{id}/messages 接口创建
-- - assistant 消息：由 LLM 响应异步创建
-- - MockProviderAdapter 需要在第一轮返回 finish_reason=tool_calls
-- - McpService.callTool 需要被 mock 返回工具执行结果
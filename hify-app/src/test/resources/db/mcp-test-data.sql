-- ============================================
-- MCP 模块集成测试预置数据
-- ============================================

-- MCP Server 1: 启用状态
INSERT INTO t_mcp_server (id, name, endpoint, enabled, status, tool_count, deleted, created_at, updated_at)
VALUES (200, 'Active Server', 'http://localhost:2000/sse', 1, 'active', 2, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- MCP Server 1 的工具
INSERT INTO t_mcp_tool (id, server_id, name, description, input_schema, deleted, created_at, updated_at)
VALUES
(201, 200, 'tool_a', 'Tool A description', '{"type":"object","properties":{"arg1":{"type":"string"}}}', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(202, 200, 'tool_b', 'Tool B description', '{"type":"object","properties":{"arg2":{"type":"number"}}}', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- MCP Server 2: 禁用状态
INSERT INTO t_mcp_server (id, name, endpoint, enabled, status, tool_count, deleted, created_at, updated_at)
VALUES (210, 'Disabled Server', 'http://localhost:2100/sse', 0, 'inactive', 1, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- MCP Server 2 的工具（Server 禁用但工具存在）
INSERT INTO t_mcp_tool (id, server_id, name, description, input_schema, deleted, created_at, updated_at)
VALUES (211, 210, 'disabled_tool', 'Disabled tool', '{"type":"object"}', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- Provider + Model + Agent（用于绑定测试）
INSERT INTO t_provider (id, code, name, provider_type, base_url, auth_type, status, timeout_ms, max_retries, sort_order, deleted)
VALUES (200, 'mcp-provider', 'MCP Provider', 'openai_compatible', 'https://api.mcp.com', 'bearer', 'active', 90000, 3, 0, 0);

INSERT INTO t_model (id, provider_id, model_code, model_name, model_type, status, sort_order, deleted)
VALUES (200, 200, 'gpt-4', 'GPT-4', 'chat', 'active', 0, 0);

INSERT INTO t_agent (id, name, model_config_id, temperature, max_tokens, max_context_turns, enabled, deleted)
VALUES (200, 'MCP Agent', 200, 0.70, 2048, 10, 1, 0);

-- Agent-Tool binding for tool call test
INSERT INTO t_agent_tool (agent_id, tool_id, created_at)
VALUES (200, 201, CURRENT_TIMESTAMP);

-- Chat session for tool call test
INSERT INTO t_chat_session (id, agent_id, title, deleted)
VALUES (200, 200, 'MCP Test Session', 0);

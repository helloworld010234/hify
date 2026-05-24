-- P0-6: 分页列表测试预置数据

-- Providers
INSERT INTO t_provider (id, code, name, provider_type, base_url, auth_type, status, health_status, timeout_ms, max_retries, sort_order, deleted)
VALUES (600, 'prov-a', 'Provider A', 'openai_compatible', 'https://a.com', 'bearer', 'active', 'healthy', 90000, 3, 1, 0);

INSERT INTO t_provider (id, code, name, provider_type, base_url, auth_type, status, health_status, timeout_ms, max_retries, sort_order, deleted)
VALUES (601, 'prov-b', 'Provider B', 'anthropic', 'https://b.com', 'api_key', 'inactive', 'unhealthy', 60000, 2, 2, 0);

INSERT INTO t_provider (id, code, name, provider_type, base_url, auth_type, status, health_status, timeout_ms, max_retries, sort_order, deleted)
VALUES (602, 'prov-deleted', 'Deleted Provider', 'ollama', 'http://local', 'none', 'active', 'unknown', 90000, 3, 3, 1);

-- Models
INSERT INTO t_model (id, provider_id, model_code, model_name, model_type, status, sort_order, deleted)
VALUES (700, 600, 'gpt-4', 'GPT-4', 'chat', 'active', 0, 0);

INSERT INTO t_model (id, provider_id, model_code, model_name, model_type, status, sort_order, deleted)
VALUES (701, 600, 'gpt-3.5', 'GPT-3.5', 'chat', 'active', 1, 0);

INSERT INTO t_model (id, provider_id, model_code, model_name, model_type, status, sort_order, deleted)
VALUES (702, 601, 'claude-3', 'Claude 3', 'chat', 'active', 0, 0);

-- Provider Health
INSERT INTO t_provider_health (id, provider_id, health_status, response_time_ms)
VALUES (1, 600, 'healthy', 120);

INSERT INTO t_provider_health (id, provider_id, health_status, response_time_ms)
VALUES (2, 601, 'unhealthy', 0);

-- Agents
INSERT INTO t_agent (id, name, description, model_config_id, temperature, max_tokens, max_context_turns, enabled, created_at, deleted)
VALUES (800, 'Agent-A', 'Desc A', 700, 0.70, 2048, 10, TRUE, '2026-01-01 10:00:00', FALSE);

INSERT INTO t_agent (id, name, description, model_config_id, temperature, max_tokens, max_context_turns, enabled, created_at, deleted)
VALUES (801, 'Agent-B', 'Desc B', 701, 0.50, 1024, 5, FALSE, '2026-01-01 09:00:00', FALSE);

-- Agent relations
INSERT INTO t_agent_knowledge_rel (agent_id, knowledge_id) VALUES (800, 10);
INSERT INTO t_agent_knowledge_rel (agent_id, knowledge_id) VALUES (800, 20);
INSERT INTO t_agent_tool (agent_id, tool_id) VALUES (800, 1001);
INSERT INTO t_agent_tool (agent_id, tool_id) VALUES (800, 1002);
INSERT INTO t_agent_tool (agent_id, tool_id) VALUES (801, 1003);

-- MCP 测试工具数据
INSERT INTO t_mcp_server (id, name, endpoint, enabled, status, tool_count, deleted)
VALUES (100, 'TestMcpServer', 'http://localhost:9001/mcp', TRUE, 'connected', 3, FALSE);

INSERT INTO t_mcp_tool (id, server_id, name, description, input_schema, deleted)
VALUES
(1001, 100, 'web_search', '网络搜索', '{"type":"object"}', FALSE),
(1002, 100, 'code_executor', '代码执行', '{"type":"object"}', FALSE),
(1003, 100, 'file_reader', '文件读取', '{"type":"object"}', FALSE);

-- P0-3: Agent 测试预置数据（Provider + Model）
INSERT INTO t_provider (id, code, name, provider_type, base_url, auth_type, status, timeout_ms, max_retries, sort_order, deleted)
VALUES (200, 'test-provider', 'Test Provider', 'openai_compatible', 'https://api.test.com', 'bearer', 'active', 90000, 3, 0, 0);

INSERT INTO t_model (id, provider_id, model_code, model_name, model_type, status, sort_order, deleted)
VALUES (300, 200, 'gpt-4o', 'GPT-4o', 'chat', 'active', 0, 0);

-- MCP 测试工具数据
INSERT INTO t_mcp_server (id, name, endpoint, enabled, status, tool_count, deleted)
VALUES (100, 'TestMcpServer', 'http://localhost:9001/mcp', TRUE, 'connected', 3, FALSE);

INSERT INTO t_mcp_tool (id, server_id, name, description, input_schema, deleted)
VALUES
(1001, 100, 'web_search', '网络搜索', '{"type":"object"}', FALSE),
(1002, 100, 'code_executor', '代码执行', '{"type":"object"}', FALSE),
(1003, 100, 'file_reader', '文件读取', '{"type":"object"}', FALSE);

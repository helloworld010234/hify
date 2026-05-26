-- Function Calling 两轮链路测试预置数据
-- provider + model + agent + chat_session + mcp_server + mcp_tool + agent_tool

INSERT INTO t_provider (id, code, name, provider_type, base_url, auth_type, status, timeout_ms, max_retries, sort_order, deleted)
VALUES (400, 'fc-provider', 'FC Provider', 'openai_compatible', 'https://api.fc.com', 'bearer', 'active', 90000, 3, 0, 0);

INSERT INTO t_model (id, provider_id, model_code, model_name, model_type, status, sort_order, deleted)
VALUES (400, 400, 'gpt-4', 'GPT-4', 'chat', 'active', 0, 0);

INSERT INTO t_agent (id, name, model_config_id, temperature, max_tokens, max_context_turns, enabled, deleted)
VALUES (400, 'FC Agent', 400, 0.70, 2048, 10, 1, 0);

INSERT INTO t_chat_session (id, agent_id, title, deleted)
VALUES (400, 400, 'FC Test Session', 0);

INSERT INTO t_mcp_server (id, name, endpoint, enabled, status, deleted)
VALUES (400, 'Refund Server', 'http://localhost:4000/sse', 1, 'active', 0);

INSERT INTO t_mcp_tool (id, server_id, name, description, input_schema, deleted)
VALUES (400, 400, 'check_refund_eligibility', 'Check if an order is eligible for refund',
        '{"type":"object","properties":{"order_id":{"type":"string","description":"Order ID"}},"required":["order_id"]}',
        0);

INSERT INTO t_agent_tool (id, agent_id, tool_id, created_at)
VALUES (400, 400, 400, CURRENT_TIMESTAMP);

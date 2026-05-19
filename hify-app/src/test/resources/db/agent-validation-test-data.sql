-- P0-4: Agent 校验测试预置数据
INSERT INTO t_provider (id, code, name, provider_type, base_url, auth_type, status, timeout_ms, max_retries, sort_order, deleted)
VALUES (200, 'test-provider', 'Test Provider', 'openai_compatible', 'https://api.test.com', 'bearer', 'active', 90000, 3, 0, 0);

INSERT INTO t_model (id, provider_id, model_code, model_name, model_type, status, sort_order, deleted)
VALUES (300, 200, 'gpt-4o', 'GPT-4o', 'chat', 'active', 0, 0);

INSERT INTO t_agent (id, name, description, model_config_id, temperature, max_tokens, max_context_turns, enabled, deleted)
VALUES (400, 'EXIST-AGENT', 'Existing agent', 300, 0.70, 2048, 10, TRUE, FALSE);

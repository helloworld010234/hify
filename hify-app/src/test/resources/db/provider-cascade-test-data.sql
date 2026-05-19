-- P2-5: Provider 删除后级联可见性测试
INSERT INTO t_provider (id, code, name, provider_type, base_url, auth_type, status, timeout_ms, max_retries, sort_order, deleted)
VALUES (1500, 'cascade-prov', 'Cascade Provider', 'openai_compatible', 'https://api.test.com', 'bearer', 'active', 90000, 3, 0, 0);

INSERT INTO t_model (id, provider_id, model_code, model_name, model_type, status, sort_order, deleted)
VALUES (1600, 1500, 'model-for-cascade', 'Model For Cascade', 'chat', 'active', 0, 0);

INSERT INTO t_agent (id, name, description, model_config_id, temperature, max_tokens, max_context_turns, enabled, deleted)
VALUES (1700, 'CascadeAgent', 'Desc', 1600, 0.70, 2048, 10, TRUE, FALSE);

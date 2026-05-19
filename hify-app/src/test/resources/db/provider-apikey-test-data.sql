-- P1-6: Provider API Key 更新测试预置数据
INSERT INTO t_provider (id, code, name, provider_type, base_url, auth_type, api_key, status, timeout_ms, max_retries, sort_order, deleted)
VALUES (1400, 'key-test', 'Key Test Provider', 'openai_compatible', 'https://api.test.com', 'bearer', 'sk-original-secret-key', 'active', 90000, 3, 0, 0);

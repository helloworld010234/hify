-- P0-5: Provider 逻辑删除测试预置数据
INSERT INTO t_provider (id, code, name, provider_type, base_url, auth_type, status, timeout_ms, max_retries, sort_order, deleted)
VALUES (500, 'delete-me', 'Delete Me', 'openai_compatible', 'https://api.test.com', 'bearer', 'active', 90000, 3, 0, 0);

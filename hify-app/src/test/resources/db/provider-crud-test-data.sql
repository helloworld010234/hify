-- Provider CRUD 集成测试预置数据
INSERT INTO t_provider (id, code, name, provider_type, base_url, auth_type, status, timeout_ms, max_retries, sort_order, deleted)
VALUES 
    (200, 'test-provider', 'Test Provider', 'openai_compatible', 'https://api.test.com', 'bearer', 'active', 90000, 3, 0, 0),
    (201, 'duplicate-code', 'DUPLICATE-NAME', 'openai_compatible', 'https://api.dup.com', 'bearer', 'active', 90000, 3, 0, 0);

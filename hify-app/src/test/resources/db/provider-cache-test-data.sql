-- P1-2: Provider 缓存一致性测试预置数据
INSERT INTO t_provider (id, code, name, provider_type, base_url, auth_type, status, health_status, timeout_ms, max_retries, sort_order, deleted)
VALUES (1000, 'cache-prov', 'Cached Provider', 'openai_compatible', 'https://api.test.com', 'bearer', 'active', 'unknown', 90000, 3, 0, 0);

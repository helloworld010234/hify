-- P1-1: Provider 连通性测试预置数据
INSERT INTO t_provider (id, code, name, provider_type, base_url, auth_type, status, health_status, consecutive_failures, timeout_ms, max_retries, sort_order, deleted)
VALUES (900, 'conn-test', 'Connection Test Provider', 'openai_compatible', 'https://api.test.com', 'bearer', 'active', 'unknown', 0, 90000, 3, 0, 0);

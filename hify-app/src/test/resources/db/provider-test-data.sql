-- P0-2: Provider 唯一性校验预置数据
INSERT INTO t_provider (id, code, name, provider_type, base_url, auth_type, status, timeout_ms, max_retries, sort_order, deleted)
VALUES (100, 'exist-code', 'EXIST-PROVIDER-NAME', 'openai_compatible', 'https://api.exist.com', 'bearer', 'active', 90000, 3, 0, 0);

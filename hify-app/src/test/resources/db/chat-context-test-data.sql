-- 对话上下文多轮正确性测试预置数据
-- 预置 20 条旧消息，使历史总数 > limit=21，触发截断

INSERT INTO t_provider (id, code, name, provider_type, base_url, auth_type, status, timeout_ms, max_retries, sort_order, deleted)
VALUES (500, 'ctx-provider', 'Context Provider', 'openai_compatible', 'https://api.ctx.com', 'bearer', 'active', 90000, 3, 0, 0);

INSERT INTO t_model (id, provider_id, model_code, model_name, model_type, status, sort_order, deleted)
VALUES (500, 500, 'gpt-4', 'GPT-4', 'chat', 'active', 0, 0);

INSERT INTO t_agent (id, name, model_config_id, temperature, max_tokens, max_context_turns, enabled, deleted)
VALUES (500, 'Context Agent', 500, 0.70, 2048, 10, 1, 0);

INSERT INTO t_chat_session (id, agent_id, title, deleted)
VALUES (500, 500, 'Context Test Session', 0);

-- 预置 20 条旧消息（10 轮旧对话），created_at 递增确保排序稳定
INSERT INTO t_chat_message (session_id, role, content, tokens, created_at, updated_at, deleted)
SELECT
    500,
    CASE WHEN x % 2 = 1 THEN 'user' ELSE 'assistant' END,
    CASE WHEN x % 2 = 1 THEN 'old-user-' || ((x + 1) / 2) ELSE 'old-resp-' || (x / 2) END,
    1,
    TIMESTAMPADD(MINUTE, x - 1, TIMESTAMP '2024-01-01 00:00:00'),
    TIMESTAMPADD(MINUTE, x - 1, TIMESTAMP '2024-01-01 00:00:00'),
    0
FROM SYSTEM_RANGE(1, 20);

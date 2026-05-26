-- Chat 普通对话链路测试预置数据
-- provider + model + agent + chat_session

INSERT INTO t_provider (id, code, name, provider_type, base_url, auth_type, status, timeout_ms, max_retries, sort_order, deleted)
VALUES (300, 'chat-provider', 'Chat Provider', 'openai_compatible', 'https://api.chat.com', 'bearer', 'active', 90000, 3, 0, 0);

INSERT INTO t_model (id, provider_id, model_code, model_name, model_type, status, sort_order, deleted)
VALUES (300, 300, 'gpt-4', 'GPT-4', 'chat', 'active', 0, 0);

INSERT INTO t_agent (id, name, model_config_id, temperature, max_tokens, max_context_turns, enabled, deleted)
VALUES (300, 'Chat Agent', 300, 0.70, 2048, 10, 1, 0);

INSERT INTO t_chat_session (id, agent_id, title, deleted)
VALUES (300, 300, '测试会话', 0);

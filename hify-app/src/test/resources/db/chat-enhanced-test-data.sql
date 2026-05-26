-- ============================================
-- Chat + Provider 增强集成测试预置数据
-- ============================================

-- 先清理可能残留的数据
DELETE FROM t_chat_message WHERE session_id = 730;
DELETE FROM t_chat_message WHERE session_id IN (700, 701, 702);
DELETE FROM t_chat_session WHERE id IN (700, 701, 702, 730);
DELETE FROM t_agent WHERE id IN (700, 701, 702);
DELETE FROM t_model WHERE id IN (700, 711, 720, 721);
DELETE FROM t_provider WHERE id IN (700, 710, 720, 721, 730);

-- Provider 1: 正常 provider（用于聊天测试）
INSERT INTO t_provider (id, code, name, provider_type, base_url, auth_type, status, timeout_ms, max_retries, sort_order, deleted)
VALUES (700, 'chat-prov', 'Chat Provider', 'openai_compatible', 'https://api.chat.com', 'bearer', 'active', 90000, 3, 0, 0);

-- Model 700
INSERT INTO t_model (id, provider_id, model_code, model_name, model_type, status, sort_order, deleted)
VALUES (700, 700, 'gpt-4', 'GPT-4', 'chat', 'active', 0, 0);

-- Agent 700: 基础 Agent（无工具/知识库/工作流）
INSERT INTO t_agent (id, name, model_config_id, temperature, max_tokens, max_context_turns, enabled, deleted)
VALUES (700, 'Basic Agent', 700, 0.70, 2048, 10, 1, 0);

INSERT INTO t_chat_session (id, agent_id, title, deleted)
VALUES (700, 700, 'Basic Chat Session', 0);

-- Agent 701: 绑定知识库
INSERT INTO t_agent (id, name, model_config_id, knowledge_base_id, temperature, max_tokens, max_context_turns, enabled, deleted)
VALUES (701, 'RAG Agent', 700, 500, 0.70, 2048, 10, 1, 0);

INSERT INTO t_chat_session (id, agent_id, title, deleted)
VALUES (701, 701, 'RAG Chat Session', 0);

-- Agent 702: 绑定工作流
INSERT INTO t_agent (id, name, model_config_id, workflow_id, temperature, max_tokens, max_context_turns, enabled, deleted)
VALUES (702, 'Workflow Agent', 700, 100, 0.70, 2048, 10, 1, 0);

INSERT INTO t_chat_session (id, agent_id, title, deleted)
VALUES (702, 702, 'Workflow Chat Session', 0);

-- Provider 710: 用于连接测试（含旧模型）
INSERT INTO t_provider (id, code, name, provider_type, base_url, auth_type, status, timeout_ms, max_retries, sort_order, deleted)
VALUES (710, 'conn-test', 'Connection Test Provider', 'openai_compatible', 'https://api.conn.com', 'bearer', 'active', 90000, 3, 0, 0);

-- 旧模型（将被删除后重新同步）
INSERT INTO t_model (id, provider_id, model_code, model_name, model_type, status, sort_order, deleted)
VALUES (711, 710, 'old-model', 'Old Model', 'chat', 'active', 0, 0);

-- Provider 720: Fallback 测试（主 provider）
INSERT INTO t_provider (id, code, name, provider_type, base_url, auth_type, status, timeout_ms, max_retries, sort_order, fallback_provider_id, deleted)
VALUES (720, 'primary', 'Primary Provider', 'openai_compatible', 'https://api.primary.com', 'bearer', 'active', 90000, 3, 0, 721, 0);

-- Provider 721: Fallback provider
INSERT INTO t_provider (id, code, name, provider_type, base_url, auth_type, status, timeout_ms, max_retries, sort_order, deleted)
VALUES (721, 'fallback', 'Fallback Provider', 'openai_compatible', 'https://api.fallback.com', 'bearer', 'active', 90000, 3, 0, 0);

INSERT INTO t_model (id, provider_id, model_code, model_name, model_type, status, sort_order, deleted)
VALUES (720, 720, 'gpt-4', 'GPT-4', 'chat', 'active', 0, 0);

INSERT INTO t_model (id, provider_id, model_code, model_name, model_type, status, sort_order, deleted)
VALUES (721, 721, 'gpt-4', 'GPT-4 Fallback', 'chat', 'active', 0, 0);

-- Provider 730: 健康检查测试
INSERT INTO t_provider (id, code, name, provider_type, base_url, auth_type, status, health_status, timeout_ms, max_retries, sort_order, deleted)
VALUES (730, 'health-prov', 'Health Provider', 'openai_compatible', 'https://api.health.com', 'bearer', 'active', 'unknown', 90000, 3, 0, 0);

-- 50 条消息（用于游标分页测试）
INSERT INTO t_chat_session (id, agent_id, title, deleted)
VALUES (730, 700, 'Pagination Session', 0);

-- 生成 50 条消息（25 轮 user+assistant）
INSERT INTO t_chat_message (id, session_id, role, content, tokens, deleted, created_at, updated_at) VALUES
(1001, 730, 'user', 'Message 1', 10, 0, DATEADD('SECOND', 1, CURRENT_TIMESTAMP), DATEADD('SECOND', 1, CURRENT_TIMESTAMP)),
(1002, 730, 'assistant', 'Reply 1', 15, 0, DATEADD('SECOND', 2, CURRENT_TIMESTAMP), DATEADD('SECOND', 2, CURRENT_TIMESTAMP)),
(1003, 730, 'user', 'Message 2', 10, 0, DATEADD('SECOND', 3, CURRENT_TIMESTAMP), DATEADD('SECOND', 3, CURRENT_TIMESTAMP)),
(1004, 730, 'assistant', 'Reply 2', 15, 0, DATEADD('SECOND', 4, CURRENT_TIMESTAMP), DATEADD('SECOND', 4, CURRENT_TIMESTAMP)),
(1005, 730, 'user', 'Message 3', 10, 0, DATEADD('SECOND', 5, CURRENT_TIMESTAMP), DATEADD('SECOND', 5, CURRENT_TIMESTAMP)),
(1006, 730, 'assistant', 'Reply 3', 15, 0, DATEADD('SECOND', 6, CURRENT_TIMESTAMP), DATEADD('SECOND', 6, CURRENT_TIMESTAMP)),
(1007, 730, 'user', 'Message 4', 10, 0, DATEADD('SECOND', 7, CURRENT_TIMESTAMP), DATEADD('SECOND', 7, CURRENT_TIMESTAMP)),
(1008, 730, 'assistant', 'Reply 4', 15, 0, DATEADD('SECOND', 8, CURRENT_TIMESTAMP), DATEADD('SECOND', 8, CURRENT_TIMESTAMP)),
(1009, 730, 'user', 'Message 5', 10, 0, DATEADD('SECOND', 9, CURRENT_TIMESTAMP), DATEADD('SECOND', 9, CURRENT_TIMESTAMP)),
(1010, 730, 'assistant', 'Reply 5', 15, 0, DATEADD('SECOND', 10, CURRENT_TIMESTAMP), DATEADD('SECOND', 10, CURRENT_TIMESTAMP)),
(1011, 730, 'user', 'Message 6', 10, 0, DATEADD('SECOND', 11, CURRENT_TIMESTAMP), DATEADD('SECOND', 11, CURRENT_TIMESTAMP)),
(1012, 730, 'assistant', 'Reply 6', 15, 0, DATEADD('SECOND', 12, CURRENT_TIMESTAMP), DATEADD('SECOND', 12, CURRENT_TIMESTAMP)),
(1013, 730, 'user', 'Message 7', 10, 0, DATEADD('SECOND', 13, CURRENT_TIMESTAMP), DATEADD('SECOND', 13, CURRENT_TIMESTAMP)),
(1014, 730, 'assistant', 'Reply 7', 15, 0, DATEADD('SECOND', 14, CURRENT_TIMESTAMP), DATEADD('SECOND', 14, CURRENT_TIMESTAMP)),
(1015, 730, 'user', 'Message 8', 10, 0, DATEADD('SECOND', 15, CURRENT_TIMESTAMP), DATEADD('SECOND', 15, CURRENT_TIMESTAMP)),
(1016, 730, 'assistant', 'Reply 8', 15, 0, DATEADD('SECOND', 16, CURRENT_TIMESTAMP), DATEADD('SECOND', 16, CURRENT_TIMESTAMP)),
(1017, 730, 'user', 'Message 9', 10, 0, DATEADD('SECOND', 17, CURRENT_TIMESTAMP), DATEADD('SECOND', 17, CURRENT_TIMESTAMP)),
(1018, 730, 'assistant', 'Reply 9', 15, 0, DATEADD('SECOND', 18, CURRENT_TIMESTAMP), DATEADD('SECOND', 18, CURRENT_TIMESTAMP)),
(1019, 730, 'user', 'Message 10', 10, 0, DATEADD('SECOND', 19, CURRENT_TIMESTAMP), DATEADD('SECOND', 19, CURRENT_TIMESTAMP)),
(1020, 730, 'assistant', 'Reply 10', 15, 0, DATEADD('SECOND', 20, CURRENT_TIMESTAMP), DATEADD('SECOND', 20, CURRENT_TIMESTAMP)),
(1021, 730, 'user', 'Message 11', 10, 0, DATEADD('SECOND', 21, CURRENT_TIMESTAMP), DATEADD('SECOND', 21, CURRENT_TIMESTAMP)),
(1022, 730, 'assistant', 'Reply 11', 15, 0, DATEADD('SECOND', 22, CURRENT_TIMESTAMP), DATEADD('SECOND', 22, CURRENT_TIMESTAMP)),
(1023, 730, 'user', 'Message 12', 10, 0, DATEADD('SECOND', 23, CURRENT_TIMESTAMP), DATEADD('SECOND', 23, CURRENT_TIMESTAMP)),
(1024, 730, 'assistant', 'Reply 12', 15, 0, DATEADD('SECOND', 24, CURRENT_TIMESTAMP), DATEADD('SECOND', 24, CURRENT_TIMESTAMP)),
(1025, 730, 'user', 'Message 13', 10, 0, DATEADD('SECOND', 25, CURRENT_TIMESTAMP), DATEADD('SECOND', 25, CURRENT_TIMESTAMP)),
(1026, 730, 'assistant', 'Reply 13', 15, 0, DATEADD('SECOND', 26, CURRENT_TIMESTAMP), DATEADD('SECOND', 26, CURRENT_TIMESTAMP)),
(1027, 730, 'user', 'Message 14', 10, 0, DATEADD('SECOND', 27, CURRENT_TIMESTAMP), DATEADD('SECOND', 27, CURRENT_TIMESTAMP)),
(1028, 730, 'assistant', 'Reply 14', 15, 0, DATEADD('SECOND', 28, CURRENT_TIMESTAMP), DATEADD('SECOND', 28, CURRENT_TIMESTAMP)),
(1029, 730, 'user', 'Message 15', 10, 0, DATEADD('SECOND', 29, CURRENT_TIMESTAMP), DATEADD('SECOND', 29, CURRENT_TIMESTAMP)),
(1030, 730, 'assistant', 'Reply 15', 15, 0, DATEADD('SECOND', 30, CURRENT_TIMESTAMP), DATEADD('SECOND', 30, CURRENT_TIMESTAMP)),
(1031, 730, 'user', 'Message 16', 10, 0, DATEADD('SECOND', 31, CURRENT_TIMESTAMP), DATEADD('SECOND', 31, CURRENT_TIMESTAMP)),
(1032, 730, 'assistant', 'Reply 16', 15, 0, DATEADD('SECOND', 32, CURRENT_TIMESTAMP), DATEADD('SECOND', 32, CURRENT_TIMESTAMP)),
(1033, 730, 'user', 'Message 17', 10, 0, DATEADD('SECOND', 33, CURRENT_TIMESTAMP), DATEADD('SECOND', 33, CURRENT_TIMESTAMP)),
(1034, 730, 'assistant', 'Reply 17', 15, 0, DATEADD('SECOND', 34, CURRENT_TIMESTAMP), DATEADD('SECOND', 34, CURRENT_TIMESTAMP)),
(1035, 730, 'user', 'Message 18', 10, 0, DATEADD('SECOND', 35, CURRENT_TIMESTAMP), DATEADD('SECOND', 35, CURRENT_TIMESTAMP)),
(1036, 730, 'assistant', 'Reply 18', 15, 0, DATEADD('SECOND', 36, CURRENT_TIMESTAMP), DATEADD('SECOND', 36, CURRENT_TIMESTAMP)),
(1037, 730, 'user', 'Message 19', 10, 0, DATEADD('SECOND', 37, CURRENT_TIMESTAMP), DATEADD('SECOND', 37, CURRENT_TIMESTAMP)),
(1038, 730, 'assistant', 'Reply 19', 15, 0, DATEADD('SECOND', 38, CURRENT_TIMESTAMP), DATEADD('SECOND', 38, CURRENT_TIMESTAMP)),
(1039, 730, 'user', 'Message 20', 10, 0, DATEADD('SECOND', 39, CURRENT_TIMESTAMP), DATEADD('SECOND', 39, CURRENT_TIMESTAMP)),
(1040, 730, 'assistant', 'Reply 20', 15, 0, DATEADD('SECOND', 40, CURRENT_TIMESTAMP), DATEADD('SECOND', 40, CURRENT_TIMESTAMP)),
(1041, 730, 'user', 'Message 21', 10, 0, DATEADD('SECOND', 41, CURRENT_TIMESTAMP), DATEADD('SECOND', 41, CURRENT_TIMESTAMP)),
(1042, 730, 'assistant', 'Reply 21', 15, 0, DATEADD('SECOND', 42, CURRENT_TIMESTAMP), DATEADD('SECOND', 42, CURRENT_TIMESTAMP)),
(1043, 730, 'user', 'Message 22', 10, 0, DATEADD('SECOND', 43, CURRENT_TIMESTAMP), DATEADD('SECOND', 43, CURRENT_TIMESTAMP)),
(1044, 730, 'assistant', 'Reply 22', 15, 0, DATEADD('SECOND', 44, CURRENT_TIMESTAMP), DATEADD('SECOND', 44, CURRENT_TIMESTAMP)),
(1045, 730, 'user', 'Message 23', 10, 0, DATEADD('SECOND', 45, CURRENT_TIMESTAMP), DATEADD('SECOND', 45, CURRENT_TIMESTAMP)),
(1046, 730, 'assistant', 'Reply 23', 15, 0, DATEADD('SECOND', 46, CURRENT_TIMESTAMP), DATEADD('SECOND', 46, CURRENT_TIMESTAMP)),
(1047, 730, 'user', 'Message 24', 10, 0, DATEADD('SECOND', 47, CURRENT_TIMESTAMP), DATEADD('SECOND', 47, CURRENT_TIMESTAMP)),
(1048, 730, 'assistant', 'Reply 24', 15, 0, DATEADD('SECOND', 48, CURRENT_TIMESTAMP), DATEADD('SECOND', 48, CURRENT_TIMESTAMP)),
(1049, 730, 'user', 'Message 25', 10, 0, DATEADD('SECOND', 49, CURRENT_TIMESTAMP), DATEADD('SECOND', 49, CURRENT_TIMESTAMP)),
(1050, 730, 'assistant', 'Reply 25', 15, 0, DATEADD('SECOND', 50, CURRENT_TIMESTAMP), DATEADD('SECOND', 50, CURRENT_TIMESTAMP));

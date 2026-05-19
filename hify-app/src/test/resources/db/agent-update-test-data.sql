-- P1-3: Agent 更新测试预置数据
INSERT INTO t_provider (id, code, name, provider_type, base_url, auth_type, status, timeout_ms, max_retries, sort_order, deleted)
VALUES (1100, 'update-prov', 'Update Provider', 'openai_compatible', 'https://api.test.com', 'bearer', 'active', 90000, 3, 0, 0);

INSERT INTO t_model (id, provider_id, model_code, model_name, model_type, status, sort_order, deleted)
VALUES (1200, 1100, 'gpt-4', 'GPT-4', 'chat', 'active', 0, 0);

INSERT INTO t_model (id, provider_id, model_code, model_name, model_type, status, sort_order, deleted)
VALUES (1201, 1100, 'gpt-3.5', 'GPT-3.5', 'chat', 'active', 1, 0);

INSERT INTO t_agent (id, name, description, model_config_id, temperature, max_tokens, max_context_turns, enabled, deleted)
VALUES (1300, 'OldAgent', 'Old desc', 1200, 0.70, 2048, 10, TRUE, FALSE);

INSERT INTO t_agent_knowledge_rel (agent_id, knowledge_id) VALUES (1300, 1);
INSERT INTO t_agent_knowledge_rel (agent_id, knowledge_id) VALUES (1300, 2);
INSERT INTO t_agent_knowledge_rel (agent_id, knowledge_id) VALUES (1300, 3);

INSERT INTO t_agent_tool (agent_id, tool_id) VALUES (1300, 100);
INSERT INTO t_agent_tool (agent_id, tool_id) VALUES (1300, 200);

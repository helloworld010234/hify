-- P2-5: Provider 删除后级联可见性测试
INSERT INTO t_provider (id, code, name, provider_type, base_url, auth_type, status, timeout_ms, max_retries, sort_order, deleted)
VALUES (1500, 'cascade-prov', 'Cascade Provider', 'openai_compatible', 'https://api.test.com', 'bearer', 'active', 90000, 3, 0, 0);

INSERT INTO t_model (id, provider_id, model_code, model_name, model_type, status, sort_order, deleted)
VALUES (1600, 1500, 'model-for-cascade', 'Model For Cascade', 'chat', 'active', 0, 0);

INSERT INTO t_agent (id, name, description, model_config_id, temperature, max_tokens, max_context_turns, enabled, deleted)
VALUES (1700, 'CascadeAgent', 'Desc', 1600, 0.70, 2048, 10, 1, 0);

INSERT INTO t_mcp_server (id, name, endpoint, enabled, status, tool_count, deleted)
VALUES (200, 'EdgeCaseMcpServer', 'http://localhost:9002/mcp', 1, 'connected', 50, 0);

-- 批量插入 50 个工具
INSERT INTO t_mcp_tool (id, server_id, name, description, input_schema, deleted)
VALUES (1001, 200, 'tool_1', 'Tool 1', '{"type":"object"}', 0);
INSERT INTO t_mcp_tool (id, server_id, name, description, input_schema, deleted)
VALUES (1002, 200, 'tool_2', 'Tool 2', '{"type":"object"}', 0);
INSERT INTO t_mcp_tool (id, server_id, name, description, input_schema, deleted)
VALUES (1003, 200, 'tool_3', 'Tool 3', '{"type":"object"}', 0);
INSERT INTO t_mcp_tool (id, server_id, name, description, input_schema, deleted)
VALUES (1004, 200, 'tool_4', 'Tool 4', '{"type":"object"}', 0);
INSERT INTO t_mcp_tool (id, server_id, name, description, input_schema, deleted)
VALUES (1005, 200, 'tool_5', 'Tool 5', '{"type":"object"}', 0);
INSERT INTO t_mcp_tool (id, server_id, name, description, input_schema, deleted)
VALUES (1006, 200, 'tool_6', 'Tool 6', '{"type":"object"}', 0);
INSERT INTO t_mcp_tool (id, server_id, name, description, input_schema, deleted)
VALUES (1007, 200, 'tool_7', 'Tool 7', '{"type":"object"}', 0);
INSERT INTO t_mcp_tool (id, server_id, name, description, input_schema, deleted)
VALUES (1008, 200, 'tool_8', 'Tool 8', '{"type":"object"}', 0);
INSERT INTO t_mcp_tool (id, server_id, name, description, input_schema, deleted)
VALUES (1009, 200, 'tool_9', 'Tool 9', '{"type":"object"}', 0);
INSERT INTO t_mcp_tool (id, server_id, name, description, input_schema, deleted)
VALUES (1010, 200, 'tool_10', 'Tool 10', '{"type":"object"}', 0);
INSERT INTO t_mcp_tool (id, server_id, name, description, input_schema, deleted)
VALUES (1011, 200, 'tool_11', 'Tool 11', '{"type":"object"}', 0);
INSERT INTO t_mcp_tool (id, server_id, name, description, input_schema, deleted)
VALUES (1012, 200, 'tool_12', 'Tool 12', '{"type":"object"}', 0);
INSERT INTO t_mcp_tool (id, server_id, name, description, input_schema, deleted)
VALUES (1013, 200, 'tool_13', 'Tool 13', '{"type":"object"}', 0);
INSERT INTO t_mcp_tool (id, server_id, name, description, input_schema, deleted)
VALUES (1014, 200, 'tool_14', 'Tool 14', '{"type":"object"}', 0);
INSERT INTO t_mcp_tool (id, server_id, name, description, input_schema, deleted)
VALUES (1015, 200, 'tool_15', 'Tool 15', '{"type":"object"}', 0);
INSERT INTO t_mcp_tool (id, server_id, name, description, input_schema, deleted)
VALUES (1016, 200, 'tool_16', 'Tool 16', '{"type":"object"}', 0);
INSERT INTO t_mcp_tool (id, server_id, name, description, input_schema, deleted)
VALUES (1017, 200, 'tool_17', 'Tool 17', '{"type":"object"}', 0);
INSERT INTO t_mcp_tool (id, server_id, name, description, input_schema, deleted)
VALUES (1018, 200, 'tool_18', 'Tool 18', '{"type":"object"}', 0);
INSERT INTO t_mcp_tool (id, server_id, name, description, input_schema, deleted)
VALUES (1019, 200, 'tool_19', 'Tool 19', '{"type":"object"}', 0);
INSERT INTO t_mcp_tool (id, server_id, name, description, input_schema, deleted)
VALUES (1020, 200, 'tool_20', 'Tool 20', '{"type":"object"}', 0);
INSERT INTO t_mcp_tool (id, server_id, name, description, input_schema, deleted)
VALUES (1021, 200, 'tool_21', 'Tool 21', '{"type":"object"}', 0);
INSERT INTO t_mcp_tool (id, server_id, name, description, input_schema, deleted)
VALUES (1022, 200, 'tool_22', 'Tool 22', '{"type":"object"}', 0);
INSERT INTO t_mcp_tool (id, server_id, name, description, input_schema, deleted)
VALUES (1023, 200, 'tool_23', 'Tool 23', '{"type":"object"}', 0);
INSERT INTO t_mcp_tool (id, server_id, name, description, input_schema, deleted)
VALUES (1024, 200, 'tool_24', 'Tool 24', '{"type":"object"}', 0);
INSERT INTO t_mcp_tool (id, server_id, name, description, input_schema, deleted)
VALUES (1025, 200, 'tool_25', 'Tool 25', '{"type":"object"}', 0);
INSERT INTO t_mcp_tool (id, server_id, name, description, input_schema, deleted)
VALUES (1026, 200, 'tool_26', 'Tool 26', '{"type":"object"}', 0);
INSERT INTO t_mcp_tool (id, server_id, name, description, input_schema, deleted)
VALUES (1027, 200, 'tool_27', 'Tool 27', '{"type":"object"}', 0);
INSERT INTO t_mcp_tool (id, server_id, name, description, input_schema, deleted)
VALUES (1028, 200, 'tool_28', 'Tool 28', '{"type":"object"}', 0);
INSERT INTO t_mcp_tool (id, server_id, name, description, input_schema, deleted)
VALUES (1029, 200, 'tool_29', 'Tool 29', '{"type":"object"}', 0);
INSERT INTO t_mcp_tool (id, server_id, name, description, input_schema, deleted)
VALUES (1030, 200, 'tool_30', 'Tool 30', '{"type":"object"}', 0);
INSERT INTO t_mcp_tool (id, server_id, name, description, input_schema, deleted)
VALUES (1031, 200, 'tool_31', 'Tool 31', '{"type":"object"}', 0);
INSERT INTO t_mcp_tool (id, server_id, name, description, input_schema, deleted)
VALUES (1032, 200, 'tool_32', 'Tool 32', '{"type":"object"}', 0);
INSERT INTO t_mcp_tool (id, server_id, name, description, input_schema, deleted)
VALUES (1033, 200, 'tool_33', 'Tool 33', '{"type":"object"}', 0);
INSERT INTO t_mcp_tool (id, server_id, name, description, input_schema, deleted)
VALUES (1034, 200, 'tool_34', 'Tool 34', '{"type":"object"}', 0);
INSERT INTO t_mcp_tool (id, server_id, name, description, input_schema, deleted)
VALUES (1035, 200, 'tool_35', 'Tool 35', '{"type":"object"}', 0);
INSERT INTO t_mcp_tool (id, server_id, name, description, input_schema, deleted)
VALUES (1036, 200, 'tool_36', 'Tool 36', '{"type":"object"}', 0);
INSERT INTO t_mcp_tool (id, server_id, name, description, input_schema, deleted)
VALUES (1037, 200, 'tool_37', 'Tool 37', '{"type":"object"}', 0);
INSERT INTO t_mcp_tool (id, server_id, name, description, input_schema, deleted)
VALUES (1038, 200, 'tool_38', 'Tool 38', '{"type":"object"}', 0);
INSERT INTO t_mcp_tool (id, server_id, name, description, input_schema, deleted)
VALUES (1039, 200, 'tool_39', 'Tool 39', '{"type":"object"}', 0);
INSERT INTO t_mcp_tool (id, server_id, name, description, input_schema, deleted)
VALUES (1040, 200, 'tool_40', 'Tool 40', '{"type":"object"}', 0);
INSERT INTO t_mcp_tool (id, server_id, name, description, input_schema, deleted)
VALUES (1041, 200, 'tool_41', 'Tool 41', '{"type":"object"}', 0);
INSERT INTO t_mcp_tool (id, server_id, name, description, input_schema, deleted)
VALUES (1042, 200, 'tool_42', 'Tool 42', '{"type":"object"}', 0);
INSERT INTO t_mcp_tool (id, server_id, name, description, input_schema, deleted)
VALUES (1043, 200, 'tool_43', 'Tool 43', '{"type":"object"}', 0);
INSERT INTO t_mcp_tool (id, server_id, name, description, input_schema, deleted)
VALUES (1044, 200, 'tool_44', 'Tool 44', '{"type":"object"}', 0);
INSERT INTO t_mcp_tool (id, server_id, name, description, input_schema, deleted)
VALUES (1045, 200, 'tool_45', 'Tool 45', '{"type":"object"}', 0);
INSERT INTO t_mcp_tool (id, server_id, name, description, input_schema, deleted)
VALUES (1046, 200, 'tool_46', 'Tool 46', '{"type":"object"}', 0);
INSERT INTO t_mcp_tool (id, server_id, name, description, input_schema, deleted)
VALUES (1047, 200, 'tool_47', 'Tool 47', '{"type":"object"}', 0);
INSERT INTO t_mcp_tool (id, server_id, name, description, input_schema, deleted)
VALUES (1048, 200, 'tool_48', 'Tool 48', '{"type":"object"}', 0);
INSERT INTO t_mcp_tool (id, server_id, name, description, input_schema, deleted)
VALUES (1049, 200, 'tool_49', 'Tool 49', '{"type":"object"}', 0);
INSERT INTO t_mcp_tool (id, server_id, name, description, input_schema, deleted)
VALUES (1050, 200, 'tool_50', 'Tool 50', '{"type":"object"}', 0);
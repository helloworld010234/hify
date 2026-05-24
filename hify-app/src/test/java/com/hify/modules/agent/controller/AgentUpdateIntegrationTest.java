package com.hify.modules.agent.controller;

import com.hify.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.jdbc.Sql;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Sql(scripts = {
        "classpath:db/agent-update-test-data.sql"
}, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class AgentUpdateIntegrationTest extends AbstractIntegrationTest {

    @Test
    @DisplayName("P1-3: Agent 更新 — 基本字段修改 + 关联全量覆盖")
    void updateAgent_shouldUpdateFieldsAndReplaceRelations() throws Exception {
        Long agentId = 1300L;

        String requestJson = """
                {
                    "name": "UpdatedAgent",
                    "description": "Updated description",
                    "systemPrompt": "Updated prompt",
                    "modelConfigId": 1201,
                    "temperature": 0.30,
                    "maxTokens": 512,
                    "maxContextTurns": 3,
                    "enabled": 0,
                    "knowledgeIds": [10, 20],
                    "toolIds": [300]
                }
                """;

        mockMvc.perform(put("/api/v1/agents/{id}", agentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        // 验证主表更新
        Map<String, Object> agent = jdbcTemplate.queryForMap(
                "SELECT * FROM t_agent WHERE id = ?", agentId);
        assertEquals("UpdatedAgent", agent.get("name"));
        assertEquals("Updated description", agent.get("description"));
        assertEquals("Updated prompt", agent.get("system_prompt"));
        assertEquals(1201L, ((Number) agent.get("model_config_id")).longValue());
        assertEquals(0.30, ((Number) agent.get("temperature")).doubleValue(), 0.001);
        assertEquals(512, agent.get("max_tokens"));
        assertEquals(3, agent.get("max_context_turns"));
        assertEquals(0, ((Number) agent.get("enabled")).intValue());

        // 验证知识库关联全量覆盖（旧 1,2,3 被删除，新 10,20 插入）
        List<Map<String, Object>> knowledges = jdbcTemplate.queryForList(
                "SELECT knowledge_id FROM t_agent_knowledge_rel WHERE agent_id = ? ORDER BY knowledge_id", agentId);
        assertEquals(2, knowledges.size());
        assertEquals(10L, ((Number) knowledges.get(0).get("knowledge_id")).longValue());
        assertEquals(20L, ((Number) knowledges.get(1).get("knowledge_id")).longValue());

        // 验证工具关联全量覆盖（旧 100,200 被删除，新 300 插入）
        List<Map<String, Object>> tools = jdbcTemplate.queryForList(
                "SELECT tool_id FROM t_agent_tool WHERE agent_id = ? ORDER BY tool_id", agentId);
        assertEquals(1, tools.size());
        assertEquals(300L, ((Number) tools.get(0).get("tool_id")).longValue());
    }

    @Test
    @DisplayName("P1-3: Agent 更新 — 不存在的 modelConfigId 应被拦截")
    void updateAgent_invalidModelConfigId_shouldReturnParamError() throws Exception {
        String requestJson = """
                {
                    "name": "UpdatedAgent",
                    "modelConfigId": 99999,
                    "enabled": 1
                }
                """;

        mockMvc.perform(put("/api/v1/agents/{id}", 1300)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(2003))
                .andExpect(jsonPath("$.message").value("模型配置不存在：99999"));

        // 数据库未被修改
        Map<String, Object> agent = jdbcTemplate.queryForMap(
                "SELECT * FROM t_agent WHERE id = 1300");
        assertEquals("OldAgent", agent.get("name"));
    }
}

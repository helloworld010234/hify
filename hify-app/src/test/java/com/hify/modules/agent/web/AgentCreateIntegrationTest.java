package com.hify.modules.agent.web;

import com.hify.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.jdbc.Sql;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Sql(scripts = {
        "classpath:db/provider-schema-h2.sql",
        "classpath:db/agent-schema-h2.sql",
        "classpath:db/agent-test-data.sql"
}, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class AgentCreateIntegrationTest extends AbstractIntegrationTest {

    @Test
    @DisplayName("P0-3: Agent 创建完整链路 — 基本字段落库 + 关联表批量插入")
    void createAgent_basicFieldsAndRelations_shouldPersistAll() throws Exception {
        String requestJson = """
                {
                    "name": "TestAgent",
                    "description": "A test agent",
                    "systemPrompt": "You are a test assistant.",
                    "modelConfigId": 300,
                    "temperature": 0.50,
                    "maxTokens": 1024,
                    "maxContextTurns": 5,
                    "enabled": 1,
                    "knowledgeIds": [10, 20],
                    "toolIds": [1001, 1002, 1003]
                }
                """;

        String response = mockMvc.perform(post("/api/v1/agents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").isNumber())
                .andExpect(jsonPath("$.data.name").value("TestAgent"))
                .andExpect(jsonPath("$.data.modelName").value("GPT-4o"))
                .andExpect(jsonPath("$.data.knowledgeIds").isArray())
                .andExpect(jsonPath("$.data.toolIds").isArray())
                .andReturn().getResponse().getContentAsString();

        Long agentId = objectMapper.readTree(response).path("data").path("id").asLong();
        assertTrue(agentId > 0);

        // 验证主表
        Map<String, Object> agent = jdbcTemplate.queryForMap(
                "SELECT * FROM t_agent WHERE id = ?", agentId);
        assertEquals("TestAgent", agent.get("name"));
        assertEquals("A test agent", agent.get("description"));
        assertEquals("You are a test assistant.", agent.get("system_prompt"));
        assertEquals(300L, ((Number) agent.get("model_config_id")).longValue());
        assertEquals(0.50, ((Number) agent.get("temperature")).doubleValue(), 0.001);
        assertEquals(1024, agent.get("max_tokens"));
        assertEquals(5, agent.get("max_context_turns"));
        assertEquals(Boolean.TRUE, agent.get("enabled"));
        assertFalse((Boolean) agent.get("deleted"));

        // 验证知识库关联表
        List<Map<String, Object>> knowledges = jdbcTemplate.queryForList(
                "SELECT knowledge_id FROM t_agent_knowledge_rel WHERE agent_id = ? ORDER BY knowledge_id", agentId);
        assertEquals(2, knowledges.size());
        assertEquals(10L, ((Number) knowledges.get(0).get("knowledge_id")).longValue());
        assertEquals(20L, ((Number) knowledges.get(1).get("knowledge_id")).longValue());

        // 验证工具关联表
        List<Map<String, Object>> tools = jdbcTemplate.queryForList(
                "SELECT tool_id FROM t_agent_tool WHERE agent_id = ? ORDER BY tool_id", agentId);
        assertEquals(3, tools.size());
        assertEquals(1001L, ((Number) tools.get(0).get("tool_id")).longValue());
        assertEquals(1002L, ((Number) tools.get(1).get("tool_id")).longValue());
        assertEquals(1003L, ((Number) tools.get(2).get("tool_id")).longValue());
    }

    @Test
    @DisplayName("P0-3: Agent 创建 — 空关联列表时不报错、关联表无数据")
    void createAgent_emptyRelations_shouldSucceedWithNoRelations() throws Exception {
        String requestJson = """
                {
                    "name": "AgentNoRel",
                    "modelConfigId": 300,
                    "knowledgeIds": [],
                    "toolIds": []
                }
                """;

        String response = mockMvc.perform(post("/api/v1/agents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn().getResponse().getContentAsString();

        Long agentId = objectMapper.readTree(response).path("data").path("id").asLong();

        List<Map<String, Object>> knowledges = jdbcTemplate.queryForList(
                "SELECT * FROM t_agent_knowledge_rel WHERE agent_id = ?", agentId);
        assertTrue(knowledges.isEmpty());

        List<Map<String, Object>> tools = jdbcTemplate.queryForList(
                "SELECT * FROM t_agent_tool WHERE agent_id = ?", agentId);
        assertTrue(tools.isEmpty());
    }
}

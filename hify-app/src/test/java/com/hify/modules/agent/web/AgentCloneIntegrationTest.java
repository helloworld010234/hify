package com.hify.modules.agent.web;

import com.hify.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
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
        "classpath:db/agent-update-test-data.sql"
}, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class AgentCloneIntegrationTest extends AbstractIntegrationTest {

    @Test
    @DisplayName("P1-4: Agent 克隆 — 名称加副本后缀、默认禁用、关联完整复制")
    void cloneAgent_shouldCreateCopyWithSuffixAndDisabled() throws Exception {
        Long sourceId = 1300L;

        String response = mockMvc.perform(post("/api/v1/agents/{id}/clone", sourceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isNumber())
                .andReturn().getResponse().getContentAsString();

        Long newId = objectMapper.readTree(response).path("data").asLong();
        assertNotEquals(sourceId, newId);

        // 验证新 Agent
        Map<String, Object> cloned = jdbcTemplate.queryForMap(
                "SELECT * FROM t_agent WHERE id = ?", newId);
        assertEquals("OldAgent 副本", cloned.get("name"));
        assertEquals("Old desc", cloned.get("description"));
        assertEquals(1200L, ((Number) cloned.get("model_config_id")).longValue());
        assertEquals(Boolean.FALSE, cloned.get("enabled"), "克隆后默认禁用");

        // 验证知识库关联被复制
        List<Map<String, Object>> knowledges = jdbcTemplate.queryForList(
                "SELECT knowledge_id FROM t_agent_knowledge_rel WHERE agent_id = ? ORDER BY knowledge_id", newId);
        assertEquals(3, knowledges.size());

        // 验证工具关联被复制
        List<Map<String, Object>> tools = jdbcTemplate.queryForList(
                "SELECT tool_id FROM t_agent_tool WHERE agent_id = ? ORDER BY tool_id", newId);
        assertEquals(2, tools.size());

        // 验证原 Agent 不受影响
        Map<String, Object> original = jdbcTemplate.queryForMap(
                "SELECT * FROM t_agent WHERE id = ?", sourceId);
        assertEquals("OldAgent", original.get("name"));
        assertEquals(Boolean.TRUE, original.get("enabled"));
    }
}

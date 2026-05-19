package com.hify.modules.provider.web;

import com.hify.AbstractIntegrationTest;
import com.hify.modules.provider.api.dto.response.ConnectionTestResult;
import com.hify.modules.provider.domain.adapter.ProviderAdapter;
import com.hify.modules.provider.domain.adapter.ProviderAdapterFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.jdbc.Sql;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Sql(scripts = {
        "classpath:db/provider-schema-h2.sql",
        "classpath:db/agent-schema-h2.sql",
        "classpath:db/provider-cascade-test-data.sql"
}, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class EdgeCaseIntegrationTest extends AbstractIntegrationTest {

    @MockBean
    private ProviderAdapterFactory adapterFactory;

    @Test
    @DisplayName("P2-4: Agent 绑定大量 tools/knowledges — 批量插入成功")
    void createAgent_withManyRelations_shouldBatchInsert() throws Exception {
        StringBuilder knowledgeIds = new StringBuilder();
        StringBuilder toolIds = new StringBuilder();
        for (int i = 1; i <= 50; i++) {
            knowledgeIds.append(i);
            toolIds.append(i + 1000);
            if (i < 50) {
                knowledgeIds.append(",");
                toolIds.append(",");
            }
        }

        String requestJson = """
                {
                    "name": "MassRelAgent",
                    "modelConfigId": 1600,
                    "knowledgeIds": [%s],
                    "toolIds": [%s]
                }
                """.formatted(knowledgeIds, toolIds);

        String response = mockMvc.perform(post("/api/v1/agents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn().getResponse().getContentAsString();

        Long agentId = objectMapper.readTree(response).path("data").path("id").asLong();

        Long knowledgeCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM t_agent_knowledge_rel WHERE agent_id = ?", Long.class, agentId);
        assertEquals(50L, knowledgeCount);

        Long toolCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM t_agent_tool WHERE agent_id = ?", Long.class, agentId);
        assertEquals(50L, toolCount);
    }

    @Test
    @DisplayName("P2-5: 删除 Provider 后，其下 Model 对 Agent 不可见")
    void deleteProvider_thenModelShouldNotBeVisibleToAgent() throws Exception {
        // 先确认 Agent 可以正常查询（modelName 有值）
        mockMvc.perform(get("/api/v1/agents/{id}", 1700))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.modelName").value("Model For Cascade"));

        // 删除 Provider
        mockMvc.perform(delete("/api/v1/providers/{id}", 1500))
                .andExpect(status().isOk());

        // 再次查询 Agent，modelName 应为空（因为 modelConfigId 指向的模型已被逻辑删除）
        mockMvc.perform(get("/api/v1/agents/{id}", 1700))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.modelName").isEmpty());
    }

    @Test
    @DisplayName("P2-6: 连续 3 次连通性测试失败，熔断计数递增并标记 unhealthy")
    void testConnection_threeConsecutiveFailures_shouldTriggerUnhealthy() throws Exception {
        ProviderAdapter adapter = mock(ProviderAdapter.class);
        when(adapterFactory.getAdapter(eq("openai_compatible"))).thenReturn(adapter);
        when(adapter.testConnection(any())).thenReturn(
                ConnectionTestResult.fail(3000L, "Timeout")
        );

        for (int i = 1; i <= 3; i++) {
            mockMvc.perform(post("/api/v1/providers/{id}/test-connection", 1500)
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.success").value(false));
        }

        Map<String, Object> provider = jdbcTemplate.queryForMap(
                "SELECT * FROM t_provider WHERE id = 1500");
        assertEquals("unhealthy", provider.get("health_status"));
        assertEquals(3, ((Number) provider.get("consecutive_failures")).intValue());

        Map<String, Object> health = jdbcTemplate.queryForMap(
                "SELECT * FROM t_provider_health WHERE provider_id = 1500");
        assertEquals(3, ((Number) health.get("consecutive_failures")).intValue());
    }
}

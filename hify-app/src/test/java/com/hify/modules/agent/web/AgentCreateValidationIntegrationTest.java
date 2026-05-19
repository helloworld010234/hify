package com.hify.modules.agent.web;

import com.hify.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.jdbc.Sql;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Sql(scripts = {
        "classpath:db/provider-schema-h2.sql",
        "classpath:db/agent-schema-h2.sql",
        "classpath:db/agent-validation-test-data.sql"
}, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class AgentCreateValidationIntegrationTest extends AbstractIntegrationTest {

    @Test
    @DisplayName("P0-4: 同名 Agent 重复创建应被拦截")
    void createAgent_duplicateName_shouldReturnParamError() throws Exception {
        String requestJson = """
                {
                    "name": "EXIST-AGENT",
                    "modelConfigId": 300,
                    "enabled": 1
                }
                """;

        mockMvc.perform(post("/api/v1/agents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("Agent 名称已存在：EXIST-AGENT"));

        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM t_agent WHERE deleted = 0", Long.class);
        assertEquals(1L, count);
    }

    @Test
    @DisplayName("P0-4: 不存在的 modelConfigId 应被拦截")
    void createAgent_invalidModelConfigId_shouldReturnParamError() throws Exception {
        String requestJson = """
                {
                    "name": "NewAgent",
                    "modelConfigId": 99999,
                    "enabled": 1
                }
                """;

        mockMvc.perform(post("/api/v1/agents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("模型配置不存在：99999"));

        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM t_agent WHERE deleted = 0 AND name = 'NewAgent'", Long.class);
        assertEquals(0L, count);
    }
}

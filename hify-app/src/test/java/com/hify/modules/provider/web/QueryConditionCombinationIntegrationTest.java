package com.hify.modules.provider.web;

import com.hify.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.jdbc.Sql;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Sql(scripts = {
        "classpath:db/provider-schema-h2.sql",
        "classpath:db/agent-schema-h2.sql",
        "classpath:db/paging-test-data.sql"
}, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class QueryConditionCombinationIntegrationTest extends AbstractIntegrationTest {

    @Test
    @DisplayName("P1-5: Provider 列表按 providerType 筛选")
    void listProviders_filterByProviderType() throws Exception {
        mockMvc.perform(get("/api/v1/providers").param("providerType", "anthropic"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.data[0].code").value("prov-b"));
    }

    @Test
    @DisplayName("P1-5: Provider 列表按 status 筛选")
    void listProviders_filterByStatus() throws Exception {
        mockMvc.perform(get("/api/v1/providers").param("status", "inactive"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.data[0].code").value("prov-b"));
    }

    @Test
    @DisplayName("P1-5: Provider 列表按 keyword 模糊搜索")
    void listProviders_filterByKeyword() throws Exception {
        mockMvc.perform(get("/api/v1/providers").param("keyword", "b"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.data[0].code").value("prov-b"));
    }

    @Test
    @DisplayName("P1-5: Provider 列表组合条件筛选")
    void listProviders_filterByCombinedConditions() throws Exception {
        mockMvc.perform(get("/api/v1/providers")
                        .param("providerType", "openai_compatible")
                        .param("status", "active"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.data[0].code").value("prov-a"));
    }

    @Test
    @DisplayName("P1-5: Agent 列表按 enabled 筛选")
    void listAgents_filterByEnabled() throws Exception {
        mockMvc.perform(get("/api/v1/agents").param("enabled", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.data[0].name").value("Agent-A"));
    }

    @Test
    @DisplayName("P1-5: Agent 列表按 modelConfigId 筛选")
    void listAgents_filterByModelConfigId() throws Exception {
        mockMvc.perform(get("/api/v1/agents").param("modelConfigId", "701"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.data[0].name").value("Agent-B"));
    }

    @Test
    @DisplayName("P1-5: Agent 列表按 keyword 模糊搜索")
    void listAgents_filterByKeyword() throws Exception {
        mockMvc.perform(get("/api/v1/agents").param("keyword", "Agent-B"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.data[0].name").value("Agent-B"));
    }
}

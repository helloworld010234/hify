package com.hify.modules.provider.web;

import com.hify.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.jdbc.Sql;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Sql(scripts = {
        "classpath:db/provider-schema-h2.sql",
        "classpath:db/agent-schema-h2.sql",
        "classpath:db/paging-test-data.sql"
}, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class PagingAndAggregationIntegrationTest extends AbstractIntegrationTest {

    @Test
    @DisplayName("P0-6: Provider 列表分页 + modelCount + responseTimeMs 聚合")
    void listProviders_shouldReturnPagedDataWithAggregation() throws Exception {
        mockMvc.perform(get("/api/v1/providers")
                        .param("current", "1")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.data[0].code").value("prov-a"))
                .andExpect(jsonPath("$.data[0].modelCount").value(2))
                .andExpect(jsonPath("$.data[0].responseTimeMs").value(120))
                .andExpect(jsonPath("$.data[1].code").value("prov-b"))
                .andExpect(jsonPath("$.data[1].modelCount").value(1))
                .andExpect(jsonPath("$.data[1].responseTimeMs").value(0));
    }

    @Test
    @DisplayName("P0-6: Agent 列表分页 + modelName 跨模块回填 + knowledgeCount/toolCount 聚合")
    void listAgents_shouldReturnPagedDataWithCrossModuleData() throws Exception {
        mockMvc.perform(get("/api/v1/agents")
                        .param("current", "1")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.data[0].name").value("Agent-A"))
                .andExpect(jsonPath("$.data[0].modelName").value("GPT-4"))
                .andExpect(jsonPath("$.data[0].knowledgeCount").value(2))
                .andExpect(jsonPath("$.data[0].toolCount").value(2))
                .andExpect(jsonPath("$.data[1].name").value("Agent-B"))
                .andExpect(jsonPath("$.data[1].modelName").value("GPT-3.5"))
                .andExpect(jsonPath("$.data[1].knowledgeCount").value(0))
                .andExpect(jsonPath("$.data[1].toolCount").value(1));
    }

    @Test
    @DisplayName("P0-6: Provider 分页参数生效 — 只返回第一页")
    void listProviders_withPageSizeOne_shouldReturnOneRecord() throws Exception {
        mockMvc.perform(get("/api/v1/providers")
                        .param("current", "1")
                        .param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.data.length()").value(1));
    }
}

package com.hify.modules.provider.controller;

import com.hify.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.jdbc.Sql;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Sql(scripts = {
        "classpath:db/provider-delete-test-data.sql"
}, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class ProviderDeleteIntegrationTest extends AbstractIntegrationTest {

    @Test
    @DisplayName("P0-5: Provider 删除后逻辑删除生效，再次查询/更新/删除返回 NOT_FOUND")
    void deleteProvider_thenQueryUpdateDelete_shouldReturnNotFound() throws Exception {
        Long providerId = 500L;

        // 1. 执行删除
        mockMvc.perform(delete("/api/v1/providers/{id}", providerId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        // 2. 验证数据库是逻辑删除（deleted=1），不是物理删除
        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT * FROM t_provider WHERE id = ?", providerId);
        assertEquals(1, ((Number) row.get("deleted")).intValue(), "应为逻辑删除");
        assertEquals("delete-me", row.get("code"), "数据仍保留");

        // 3. 再次查询返回 404
        mockMvc.perform(get("/api/v1/providers/{id}", providerId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(2000))
                .andExpect(jsonPath("$.message").value("供应商不存在: 500"));

        // 4. 再次删除返回 404
        mockMvc.perform(delete("/api/v1/providers/{id}", providerId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(2000));

        // 5. 列表查询不出现
        String listResponse = mockMvc.perform(get("/api/v1/providers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn().getResponse().getContentAsString();

        int total = objectMapper.readTree(listResponse).path("data").path("total").asInt();
        assertEquals(0, total, "列表应无记录");
    }
}

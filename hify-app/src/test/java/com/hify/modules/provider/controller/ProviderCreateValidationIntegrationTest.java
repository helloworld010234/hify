package com.hify.modules.provider.controller;

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
        "classpath:db/provider-test-data.sql"
}, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class ProviderCreateValidationIntegrationTest extends AbstractIntegrationTest {

    @Test
    @DisplayName("P0-2: 重复 code 创建应被拦截")
    void createProvider_duplicateCode_shouldReturnParamError() throws Exception {
        String requestJson = """
                {
                    "code": "EXIST-CODE",
                    "name": "ANOTHER-NAME",
                    "providerType": "openai_compatible",
                    "baseUrl": "https://api.test.com",
                    "status": "active"
                }
                """;

        mockMvc.perform(post("/api/v1/providers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1000))
                .andExpect(jsonPath("$.message").value("供应商编码已存在: exist-code"));

        // 数据库仍只有一条记录
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM t_provider WHERE deleted = 0", Long.class);
        assertEquals(1L, count);
    }

    @Test
    @DisplayName("P0-2: 重复 name 创建应被拦截")
    void createProvider_duplicateName_shouldReturnParamError() throws Exception {
        String requestJson = """
                {
                    "code": "NEW-CODE",
                    "name": "EXIST-PROVIDER-NAME",
                    "providerType": "openai_compatible",
                    "baseUrl": "https://api.test.com",
                    "status": "active"
                }
                """;

        mockMvc.perform(post("/api/v1/providers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1000))
                .andExpect(jsonPath("$.message").value("供应商名称已存在: EXIST-PROVIDER-NAME"));

        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM t_provider WHERE deleted = 0", Long.class);
        assertEquals(1L, count);
    }
}

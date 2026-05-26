package com.hify.modules.provider.controller;

import com.hify.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.jdbc.Sql;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Provider CRUD 完整链路集成测试
 *
 * <p>覆盖：创建、查询、更新、删除，以及重复名称校验。</p>
 */
class ProviderCrudIntegrationTest extends AbstractIntegrationTest {

    @Test
    @DisplayName("P0: 创建 Provider — 合法请求，返回 id")
    void createProvider_validRequest_shouldReturnId() throws Exception {
        // Given
        String requestJson = """
                {
                    "code": "new-provider",
                    "name": "New Provider",
                    "providerType": "openai_compatible",
                    "baseUrl": "https://api.new.com",
                    "status": "active"
                }
                """;

        // When & Then
        String response = mockMvc.perform(post("/api/v1/providers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isNumber())
                .andReturn().getResponse().getContentAsString();

        Long id = objectMapper.readTree(response).path("data").asLong();
        assertThat(id).isPositive();

        // 验证数据库落库
        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT * FROM t_provider WHERE id = ?", id);
        assertThat(row.get("code")).isEqualTo("new-provider");
        assertThat(row.get("name")).isEqualTo("New Provider");
        assertThat(row.get("provider_type")).isEqualTo("openai_compatible");
        assertThat(row.get("base_url")).isEqualTo("https://api.new.com");
        assertThat(row.get("status")).isEqualTo("active");
        assertThat(row.get("deleted")).isEqualTo(0);
    }

    @Test
    @DisplayName("P0: 创建 Provider — 重复名称，返回 2001")
    @Sql(scripts = "classpath:db/provider-crud-test-data.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
    void createProvider_duplicateName_shouldReturn2001() throws Exception {
        // Given
        String requestJson = """
                {
                    "code": "unique-code",
                    "name": "DUPLICATE-NAME",
                    "providerType": "openai_compatible",
                    "baseUrl": "https://api.test.com",
                    "status": "active"
                }
                """;

        // When & Then
        mockMvc.perform(post("/api/v1/providers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(2001))
                .andExpect(jsonPath("$.message").value("供应商名称已存在: DUPLICATE-NAME"));
    }

    @Test
    @DisplayName("P0: 查询 Provider — 存在，返回完整字段")
    @Sql(scripts = "classpath:db/provider-crud-test-data.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
    void getProvider_existing_shouldReturnDetail() throws Exception {
        // When & Then
        mockMvc.perform(get("/api/v1/providers/200"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(200))
                .andExpect(jsonPath("$.data.code").value("test-provider"))
                .andExpect(jsonPath("$.data.name").value("Test Provider"))
                .andExpect(jsonPath("$.data.providerType").value("openai_compatible"))
                .andExpect(jsonPath("$.data.baseUrl").value("https://api.test.com"))
                .andExpect(jsonPath("$.data.authType").value("bearer"))
                .andExpect(jsonPath("$.data.status").value("active"));
    }

    @Test
    @DisplayName("P0: 查询 Provider — 不存在，返回 2000")
    void getProvider_notExisting_shouldReturn2000() throws Exception {
        // When & Then
        mockMvc.perform(get("/api/v1/providers/99999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(2000))
                .andExpect(jsonPath("$.message").value("供应商不存在: 99999"));
    }

    @Test
    @DisplayName("P0: 更新 Provider — 验证数据库字段确实变更")
    @Sql(scripts = "classpath:db/provider-crud-test-data.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
    void updateProvider_shouldChangeDatabase() throws Exception {
        // Given
        String requestJson = """
                {
                    "name": "Updated Provider",
                    "baseUrl": "https://api.updated.com",
                    "status": "inactive"
                }
                """;

        // When
        mockMvc.perform(put("/api/v1/providers/200")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        // Then：直接查库验证字段已变更
        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT * FROM t_provider WHERE id = 200");
        assertThat(row.get("name")).isEqualTo("Updated Provider");
        assertThat(row.get("base_url")).isEqualTo("https://api.updated.com");
        assertThat(row.get("status")).isEqualTo("inactive");
    }

    @Test
    @DisplayName("P0: 删除 Provider — 验证逻辑删除标志")
    @Sql(scripts = "classpath:db/provider-crud-test-data.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
    void deleteProvider_shouldSetDeletedFlag() throws Exception {
        // When
        mockMvc.perform(delete("/api/v1/providers/200"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        // Then：验证逻辑删除，不是物理删除
        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT * FROM t_provider WHERE id = 200");
        assertThat(row.get("deleted")).isEqualTo(1);
    }
}

package com.hify.modules.provider.web;

import com.hify.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.jdbc.Sql;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Sql(scripts = {
        "classpath:db/provider-schema-h2.sql"
}, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class ProviderCreateIntegrationTest extends AbstractIntegrationTest {

    @Test
    @DisplayName("P0-1: Provider 创建完整链路 — 基本字段落库正确，返回主键")
    void createProvider_basicFields_shouldPersistAndReturnId() throws Exception {
        String requestJson = """
                {
                    "code": "TEST-PROV",
                    "name": "测试供应商",
                    "providerType": "openai_compatible",
                    "baseUrl": "https://api.test.com",
                    "apiKey": "sk-test1234567890",
                    "status": "active"
                }
                """;

        String response = mockMvc.perform(post("/api/v1/providers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isNumber())
                .andReturn().getResponse().getContentAsString();

        Long id = objectMapper.readTree(response).path("data").asLong();
        assertTrue(id > 0, "返回的主键应为正数");

        // 直接查库验证
        var row = jdbcTemplate.queryForMap(
                "SELECT * FROM t_provider WHERE id = ?", id);

        assertEquals("test-prov", row.get("code"), "code 应自动转小写");
        assertEquals("测试供应商", row.get("name"));
        assertEquals("openai_compatible", row.get("provider_type"));
        assertEquals("https://api.test.com", row.get("base_url"));
        assertEquals("bearer", row.get("auth_type"), "openai_compatible 默认推断 bearer");
        assertEquals("active", row.get("status"));
        assertEquals(90000, row.get("timeout_ms"));
        assertEquals(3, row.get("max_retries"));
        assertEquals(0, row.get("sort_order"));
        assertEquals("unknown", row.get("health_status"));
        assertEquals(0, row.get("consecutive_failures"));
        assertFalse((Boolean) row.get("deleted"), "不应被逻辑删除");
    }

    @Test
    @DisplayName("P0-1: Provider 创建 — code 为空时自动生成，且去重")
    void createProvider_autoGenerateCode_whenCodeIsBlank() throws Exception {
        String requestJson = """
                {
                    "name": "Auto Code Provider",
                    "providerType": "ollama",
                    "baseUrl": "http://localhost:11434",
                    "status": "active"
                }
                """;

        mockMvc.perform(post("/api/v1/providers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        // 查库验证 code 已自动生成
        String code = jdbcTemplate.queryForObject(
                "SELECT code FROM t_provider WHERE name = ?", String.class, "Auto Code Provider");
        assertNotNull(code);
        assertEquals("auto_code_provider", code, "code 应由名称自动生成并转小写");
    }

    @Test
    @DisplayName("P0-1: Provider 创建 — anthropic 类型自动推断 auth_type 为 api_key")
    void createProvider_anthropic_shouldInferApiKeyAuthType() throws Exception {
        String requestJson = """
                {
                    "name": "Anthropic Provider",
                    "providerType": "anthropic",
                    "baseUrl": "https://api.anthropic.com",
                    "status": "active"
                }
                """;

        mockMvc.perform(post("/api/v1/providers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isOk());

        String authType = jdbcTemplate.queryForObject(
                "SELECT auth_type FROM t_provider WHERE name = ?", String.class, "Anthropic Provider");
        assertEquals("api_key", authType);
    }

    @Test
    @DisplayName("P0-1: Provider 创建 — ollama 类型自动推断 auth_type 为 none")
    void createProvider_ollama_shouldInferNoneAuthType() throws Exception {
        String requestJson = """
                {
                    "name": "Ollama Local",
                    "providerType": "ollama",
                    "baseUrl": "http://localhost:11434",
                    "status": "active"
                }
                """;

        mockMvc.perform(post("/api/v1/providers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isOk());

        String authType = jdbcTemplate.queryForObject(
                "SELECT auth_type FROM t_provider WHERE name = ?", String.class, "Ollama Local");
        assertEquals("none", authType);
    }
}

package com.hify.modules.provider.web;

import com.hify.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.jdbc.Sql;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Sql(scripts = {
        "classpath:db/provider-schema-h2.sql",
        "classpath:db/provider-apikey-test-data.sql"
}, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class ProviderUpdateApiKeyIntegrationTest extends AbstractIntegrationTest {

    @Test
    @DisplayName("P1-6: Provider 更新时 apiKey 为空不应覆盖原值")
    void updateProvider_emptyApiKey_shouldNotModifyExistingKey() throws Exception {
        String requestJson = """
                {
                    "name": "Key Test Provider Updated",
                    "baseUrl": "https://api.new.com",
                    "authType": "bearer",
                    "status": "active"
                }
                """;

        mockMvc.perform(put("/api/v1/providers/{id}", 1400)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        Map<String, Object> provider = jdbcTemplate.queryForMap(
                "SELECT * FROM t_provider WHERE id = 1400");
        assertEquals("sk-original-secret-key", provider.get("api_key"));
        assertEquals("Key Test Provider Updated", provider.get("name"));
    }

    @Test
    @DisplayName("P1-6: Provider 更新时传新 apiKey 应覆盖原值")
    void updateProvider_withNewApiKey_shouldReplaceExistingKey() throws Exception {
        String requestJson = """
                {
                    "name": "Key Test Provider Updated",
                    "baseUrl": "https://api.new.com",
                    "authType": "bearer",
                    "apiKey": "sk-new-secret-key",
                    "status": "active"
                }
                """;

        mockMvc.perform(put("/api/v1/providers/{id}", 1400)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        Map<String, Object> provider = jdbcTemplate.queryForMap(
                "SELECT * FROM t_provider WHERE id = 1400");
        assertEquals("sk-new-secret-key", provider.get("api_key"));
    }
}

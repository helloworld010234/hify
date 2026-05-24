package com.hify.modules.provider.controller;

import com.hify.AbstractIntegrationTest;
import com.hify.common.service.EncryptionService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.jdbc.Sql;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Sql(scripts = {
        "classpath:db/provider-apikey-test-data.sql"
}, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class ProviderUpdateApiKeyIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private EncryptionService encryptionService;

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
        // 请求中未传 apiKey，原值应保持不变（SQL 脚本预置的是明文）
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
        String dbApiKey = (String) provider.get("api_key");
        // 验证数据库中存储的是密文而非明文
        assertNotEquals("sk-new-secret-key", dbApiKey);
        // 解密后验证值正确
        String decryptedApiKey = encryptionService.decrypt(dbApiKey);
        assertEquals("sk-new-secret-key", decryptedApiKey);
    }
}

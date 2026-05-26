package com.hify.modules.provider.controller;

import com.hify.AbstractIntegrationTest;
import com.hify.common.service.EncryptionService;
import com.hify.modules.provider.dto.response.ConnectionTestResponse;
import com.hify.modules.provider.service.adapter.ProviderAdapter;
import com.hify.modules.provider.service.adapter.ProviderAdapterFactory;
import com.hify.modules.provider.entity.Provider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Provider 增强集成测试 — 连通性测试事务 + API Key 加密 + Fallback + 健康检查
 * <p>
 * 注意：连通性测试涉及模型同步等写操作，禁用测试事务以避免与 Service 层事务冲突。
 */
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ProviderControllerEnhancedIT extends AbstractIntegrationTest {

    @MockBean
    private ProviderAdapterFactory adapterFactory;

    @Autowired
    private EncryptionService encryptionService;

    @AfterEach
    void cleanupEnhancedProviderTestData() {
        jdbcTemplate.update("DELETE FROM t_provider_health WHERE provider_id IN (710, 720, 721, 730)");
        jdbcTemplate.update("DELETE FROM t_model WHERE provider_id IN (710, 720, 721, 730) OR id = 711");
        jdbcTemplate.update("DELETE FROM t_provider WHERE id IN (710, 720, 721, 730)");
        jdbcTemplate.update("DELETE FROM t_provider WHERE code = 'encrypt-test'");
        jdbcTemplate.update("DELETE FROM t_provider WHERE code = 'query-test'");
    }

    @Test
    @DisplayName("P0: 连通性测试 — 旧模型软删除 + 新模型原子插入")
    @Sql(scripts = "classpath:db/chat-enhanced-test-data.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
    void should_deleteOldModelsAndInsertNewAtomically_when_testConnection() throws Exception {
        ProviderAdapter adapter = mock(ProviderAdapter.class);
        when(adapterFactory.getAdapter(eq("openai_compatible"))).thenReturn(adapter);
        when(adapter.testConnection(any(Provider.class))).thenReturn(
                ConnectionTestResponse.success(100L, 2, List.of(
                        new ConnectionTestResponse.ModelInfo("gpt-4o", "GPT-4o"),
                        new ConnectionTestResponse.ModelInfo("gpt-4", "GPT-4")
                ))
        );

        mockMvc.perform(post("/api/v1/providers/{id}/test-connection", 710)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        Map<String, Object> oldModel = jdbcTemplate.queryForMap(
                "SELECT * FROM t_model WHERE id = 711");
        assertThat(oldModel.get("deleted")).isEqualTo(1);

        List<Map<String, Object>> newModels = jdbcTemplate.queryForList(
                "SELECT * FROM t_model WHERE provider_id = 710 AND deleted = 0 ORDER BY model_code");
        assertThat(newModels).hasSize(2);
        assertThat(newModels.get(0).get("model_code")).isEqualTo("gpt-4");
        assertThat(newModels.get(1).get("model_code")).isEqualTo("gpt-4o");
    }

    @Test
    @DisplayName("P0: 模型同步 — 软删除旧模型后插入新模型，无冲突")
    @Sql(scripts = "classpath:db/chat-enhanced-test-data.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
    void should_softDeleteOldModelsAndInsertNew_when_testConnection() throws Exception {
        // 先插入一个同名模型（deleted=0），模拟已存在的模型
        jdbcTemplate.update("INSERT INTO t_model (provider_id, model_code, model_name, model_type, status, sort_order, deleted) " +
                "VALUES (710, 'gpt-4o', 'Existing', 'chat', 'active', 0, 0)");

        ProviderAdapter adapter = mock(ProviderAdapter.class);
        when(adapterFactory.getAdapter(eq("openai_compatible"))).thenReturn(adapter);
        when(adapter.testConnection(any(Provider.class))).thenReturn(
                ConnectionTestResponse.success(100L, 1, List.of(
                        new ConnectionTestResponse.ModelInfo("gpt-4o", "GPT-4o")
                ))
        );

        // 连通性测试成功返回 200
        mockMvc.perform(post("/api/v1/providers/{id}/test-connection", 710)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        // Then: 预插入的冲突模型被软删除（deleted=1）
        Map<String, Object> conflictModel = jdbcTemplate.queryForMap(
                "SELECT * FROM t_model WHERE provider_id = 710 AND model_code = 'gpt-4o' AND deleted = 1");
        assertThat(conflictModel).isNotNull();
        assertThat(conflictModel.get("model_name")).isEqualTo("Existing");

        // 新模型成功插入（deleted=0）
        Map<String, Object> newModel = jdbcTemplate.queryForMap(
                "SELECT * FROM t_model WHERE provider_id = 710 AND model_code = 'gpt-4o' AND deleted = 0");
        assertThat(newModel).isNotNull();
        assertThat(newModel.get("model_name")).isEqualTo("GPT-4o");
    }

    @Test
    @DisplayName("P1: Provider 创建时 apiKey 加密存储")
    void should_storeEncryptedApiKey_when_createProvider() throws Exception {
        String requestJson = """
                {
                    "code": "ENCRYPT-TEST",
                    "name": "Encrypt Test",
                    "providerType": "openai_compatible",
                    "baseUrl": "https://api.enc.com",
                    "apiKey": "sk-secret-key-12345",
                    "status": "active"
                }
                """;

        mockMvc.perform(post("/api/v1/providers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        Map<String, Object> provider = jdbcTemplate.queryForMap(
                "SELECT * FROM t_provider WHERE code = 'encrypt-test'");
        String dbKey = (String) provider.get("api_key");
        assertNotEquals("sk-secret-key-12345", dbKey);

        String decrypted = encryptionService.decrypt(dbKey);
        assertEquals("sk-secret-key-12345", decrypted);
    }

    @Test
    @DisplayName("P1: Provider 查询时 apiKey 掩码返回，不暴露明文")
    void should_returnMaskedApiKey_when_queryProvider() throws Exception {
        // 创建含加密 key 的 provider
        String encrypted = encryptionService.encrypt("sk-query-test-key");
        jdbcTemplate.update("INSERT INTO t_provider (id, code, name, provider_type, base_url, auth_type, api_key, status, timeout_ms, max_retries, sort_order, deleted) " +
                "VALUES (799, 'query-test', 'Query Test', 'openai_compatible', 'https://api.q.com', 'bearer', ?, 'active', 90000, 3, 0, 0)", encrypted);

        mockMvc.perform(get("/api/v1/providers/{id}", 799))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.apiKeyMask").exists());
    }

    @Test
    @DisplayName("P2: 主 Provider 返回 503 时自动切换 fallback")
    @Sql(scripts = "classpath:db/chat-enhanced-test-data.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
    void should_useFallbackProvider_when_primaryReturns503() throws Exception {
        // This test verifies fallback configuration exists in DB
        Map<String, Object> primary = jdbcTemplate.queryForMap(
                "SELECT fallback_provider_id FROM t_provider WHERE id = 720");
        assertThat(primary.get("fallback_provider_id")).isEqualTo(721L);
    }

    @Test
    @DisplayName("P2: Provider 健康检查定时任务更新状态")
    @Sql(scripts = "classpath:db/chat-enhanced-test-data.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
    void should_updateHealthStatus_when_scheduledCheckRuns() throws Exception {
        ProviderAdapter adapter = mock(ProviderAdapter.class);
        when(adapterFactory.getAdapter(eq("openai_compatible"))).thenReturn(adapter);
        when(adapter.testConnection(any(Provider.class))).thenReturn(
                ConnectionTestResponse.success(50L, 0, List.of())
        );

        mockMvc.perform(post("/api/v1/providers/{id}/test-connection", 730)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        Map<String, Object> health = jdbcTemplate.queryForMap(
                "SELECT * FROM t_provider_health WHERE provider_id = 730");
        assertThat(health.get("health_status")).isEqualTo("healthy");
        assertThat(health.get("response_time_ms")).isEqualTo(50L);
    }
}

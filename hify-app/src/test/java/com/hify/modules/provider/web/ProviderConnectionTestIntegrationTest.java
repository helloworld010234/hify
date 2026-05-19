package com.hify.modules.provider.web;

import com.hify.AbstractIntegrationTest;
import com.hify.modules.provider.api.dto.response.ConnectionTestResult;
import com.hify.modules.provider.domain.adapter.ProviderAdapter;
import com.hify.modules.provider.domain.adapter.ProviderAdapterFactory;
import com.hify.modules.provider.infra.entity.Provider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.jdbc.Sql;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Sql(scripts = {
        "classpath:db/provider-schema-h2.sql",
        "classpath:db/provider-connection-test-data.sql"
}, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class ProviderConnectionTestIntegrationTest extends AbstractIntegrationTest {

    @MockBean
    private ProviderAdapterFactory adapterFactory;

    @Test
    @DisplayName("P1-1: 连通性测试成功 — 更新健康状态 + 同步模型列表")
    void testConnection_success_shouldUpdateHealthAndSyncModels() throws Exception {
        ProviderAdapter adapter = mock(ProviderAdapter.class);
        when(adapterFactory.getAdapter(eq("openai_compatible"))).thenReturn(adapter);
        when(adapter.testConnection(any(Provider.class))).thenReturn(
                ConnectionTestResult.success(150L, 2, List.of(
                        new ConnectionTestResult.ModelInfo("gpt-4o", "GPT-4o"),
                        new ConnectionTestResult.ModelInfo("gpt-4", "GPT-4")
                ))
        );

        mockMvc.perform(post("/api/v1/providers/{id}/test-connection", 900)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.success").value(true))
                .andExpect(jsonPath("$.data.latencyMs").value(150))
                .andExpect(jsonPath("$.data.modelCount").value(2));

        // 验证 t_provider 健康状态更新
        Map<String, Object> provider = jdbcTemplate.queryForMap(
                "SELECT * FROM t_provider WHERE id = 900");
        assertEquals("healthy", provider.get("health_status"));
        assertEquals(0, ((Number) provider.get("consecutive_failures")).intValue());
        assertNotNull(provider.get("last_check_time"));

        // 验证 t_provider_health 快照
        Map<String, Object> health = jdbcTemplate.queryForMap(
                "SELECT * FROM t_provider_health WHERE provider_id = 900");
        assertEquals("healthy", health.get("health_status"));
        assertEquals(150L, ((Number) health.get("response_time_ms")).longValue());

        // 验证 t_model 模型同步
        List<Map<String, Object>> models = jdbcTemplate.queryForList(
                "SELECT * FROM t_model WHERE provider_id = 900 ORDER BY sort_order");
        assertEquals(2, models.size());
        assertEquals("gpt-4o", models.get(0).get("model_code"));
        assertEquals("GPT-4o", models.get(0).get("model_name"));
        assertEquals("gpt-4", models.get(1).get("model_code"));
        assertEquals("GPT-4", models.get(1).get("model_name"));
    }

    @Test
    @DisplayName("P1-1: 连通性测试失败 — 更新健康状态 + 累加熔断计数")
    void testConnection_failure_shouldUpdateHealthAndIncrementFailureCount() throws Exception {
        // 先给 provider 设置一个已有的失败计数
        jdbcTemplate.update("UPDATE t_provider SET consecutive_failures = 2 WHERE id = 900");

        ProviderAdapter adapter = mock(ProviderAdapter.class);
        when(adapterFactory.getAdapter(eq("openai_compatible"))).thenReturn(adapter);
        when(adapter.testConnection(any(Provider.class))).thenReturn(
                ConnectionTestResult.fail(5000L, "Connection timeout")
        );

        mockMvc.perform(post("/api/v1/providers/{id}/test-connection", 900)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.success").value(false))
                .andExpect(jsonPath("$.data.errorMessage").value("Connection timeout"));

        // 验证 t_provider 熔断计数递增
        Map<String, Object> provider = jdbcTemplate.queryForMap(
                "SELECT * FROM t_provider WHERE id = 900");
        assertEquals("unhealthy", provider.get("health_status"));
        assertEquals(3, ((Number) provider.get("consecutive_failures")).intValue());

        // 验证 t_provider_health 快照
        Map<String, Object> health = jdbcTemplate.queryForMap(
                "SELECT * FROM t_provider_health WHERE provider_id = 900");
        assertEquals("unhealthy", health.get("health_status"));
        assertEquals(3, ((Number) health.get("consecutive_failures")).intValue());
        assertEquals("Connection timeout", health.get("last_error_msg"));
    }
}

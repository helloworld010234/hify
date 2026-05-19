package com.hify.modules.provider.domain.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hify.modules.provider.api.dto.response.ConnectionTestResult;
import com.hify.modules.provider.infra.client.LlmHttpClient;
import com.hify.modules.provider.infra.entity.Provider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * 供应商连通性检查器
 * <p>
 * 根据 {@link Provider#getProviderType()} 分发到不同的测试策略，
 * 统一返回 {@link ConnectionTestResult}。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProviderHealthChecker {

    private final LlmHttpClient httpClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 执行连通性测试
     *
     * @param provider    供应商配置
     * @param plainApiKey 明文 API Key（已解密）
     * @return 测试结果
     */
    public ConnectionTestResult test(Provider provider, String plainApiKey) {
        long start = System.currentTimeMillis();
        try {
            String responseBody = dispatchTest(provider, plainApiKey);
            long latency = System.currentTimeMillis() - start;
            int modelCount = parseModelCount(responseBody, provider.getProviderType());
            return ConnectionTestResult.success(latency, modelCount);
        } catch (Exception e) {
            long latency = System.currentTimeMillis() - start;
            log.warn("Provider health check failed: code={}, type={}, error={}",
                    provider.getCode(), provider.getProviderType(), e.getMessage());
            return ConnectionTestResult.fail(latency, e.getMessage());
        }
    }

    /**
     * 根据供应商类型分发测试请求
     */
    private String dispatchTest(Provider provider, String plainApiKey) throws IOException {
        String type = provider.getProviderType();
        String baseUrl = provider.getBaseUrl().replaceAll("/+$", ""); // 去末尾斜杠

        return switch (type) {
            case "openai", "openai_compatible" -> testOpenAiCompatible(baseUrl, plainApiKey);
            case "anthropic" -> testAnthropic(baseUrl, plainApiKey, provider.getAuthConfig());
            case "ollama" -> testOllama(baseUrl);
            default -> throw new IllegalArgumentException("不支持的供应商类型: " + type);
        };
    }

    // ==================== 各供应商测试方法 ====================

    /**
     * OpenAI / OpenAI-Compatible：GET /v1/models，Bearer Token
     */
    private String testOpenAiCompatible(String baseUrl, String apiKey) throws IOException {
        String url = baseUrl + "/v1/models";
        Map<String, String> headers = new HashMap<>();
        if (apiKey != null && !apiKey.isBlank()) {
            headers.put("Authorization", "Bearer " + apiKey);
        }
        log.debug("Testing OpenAI-compatible: {}", url);
        return httpClient.get(url, headers);
    }

    /**
     * Anthropic：GET /v1/models，x-api-key + anthropic-version
     */
    private String testAnthropic(String baseUrl, String apiKey, Object authConfigObj) throws IOException {
        String url = baseUrl + "/v1/models";
        Map<String, String> headers = new HashMap<>();
        headers.put("x-api-key", apiKey);

        // 从 authConfig 中提取 apiVersion
        String apiVersion = "2023-06-01"; // 默认值
        if (authConfigObj instanceof com.hify.modules.provider.domain.vo.AuthConfig authConfig) {
            if (authConfig.getApiVersion() != null) {
                apiVersion = authConfig.getApiVersion();
            }
        }
        headers.put("anthropic-version", apiVersion);

        log.debug("Testing Anthropic: {}, version={}", url, apiVersion);
        return httpClient.get(url, headers);
    }

    /**
     * Ollama：GET /api/tags，无认证
     */
    private String testOllama(String baseUrl) throws IOException {
        String url = baseUrl + "/api/tags";
        log.debug("Testing Ollama: {}", url);
        return httpClient.get(url, null);
    }

    // ==================== 响应解析 ====================

    /**
     * 从响应体中解析模型数量
     */
    private int parseModelCount(String responseBody, String providerType) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            return switch (providerType) {
                case "openai", "openai_compatible", "anthropic" -> {
                    JsonNode data = root.get("data");
                    yield data != null && data.isArray() ? data.size() : 0;
                }
                case "ollama" -> {
                    JsonNode models = root.get("models");
                    yield models != null && models.isArray() ? models.size() : 0;
                }
                default -> 0;
            };
        } catch (Exception e) {
            log.warn("Failed to parse model count: {}", e.getMessage());
            return 0;
        }
    }
}

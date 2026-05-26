package com.hify.modules.provider.service.adapter.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.hify.modules.provider.dto.chat.ChatRequest;
import com.hify.modules.provider.dto.chat.ChatResponse;
import com.hify.modules.provider.dto.response.ConnectionTestResponse;
import com.hify.modules.provider.dto.vo.AuthConfig;
import com.hify.modules.provider.client.LlmHttpClient;
import com.hify.modules.provider.entity.Provider;
import com.hify.modules.provider.service.adapter.OpenAiAdapter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Azure OpenAI 适配器。
 * <p>
 * 继承 {@link OpenAiAdapter} 复用 chat / streamChat 请求体构造与响应解析逻辑，
 * 仅覆盖 URL 构造（deployment 路径 + api-version）和认证 Header（api-key）。
 */
@Slf4j
@Component
@Profile("!mock")
public class AzureOpenAiAdapter extends OpenAiAdapter {

    public AzureOpenAiAdapter(LlmHttpClient httpClient) {
        super(httpClient);
    }

    @Override
    public List<ConnectionTestResponse.ModelInfo> listModels(Provider provider) throws IOException {
        String url = getModelsUrl(provider);
        Map<String, String> headers = buildAuthHeaders(provider);
        String responseBody = httpClient.get(url, headers);
        return parseAzureModels(responseBody);
    }

    @Override
    protected String getModelsUrl(Provider provider) {
        String baseUrl = provider.getBaseUrl().replaceAll("/+$", "");
        String apiVersion = getAzureApiVersion(provider);
        return baseUrl + "/openai/models?api-version=" + apiVersion;
    }

    @Override
    protected String getChatUrl(Provider provider) {
        String baseUrl = provider.getBaseUrl().replaceAll("/+$", "");
        String deploymentName = getDeploymentName(provider);
        String apiVersion = getAzureApiVersion(provider);
        return baseUrl + "/openai/deployments/" + deploymentName
                + "/chat/completions?api-version=" + apiVersion;
    }

    @Override
    protected Map<String, String> buildAuthHeaders(Provider provider) {
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json");
        headers.put("api-key", provider.getApiKey());
        return headers;
    }

    private List<ConnectionTestResponse.ModelInfo> parseAzureModels(String responseBody) throws IOException {
        List<ConnectionTestResponse.ModelInfo> models = new ArrayList<>();
        JsonNode root = objectMapper.readTree(responseBody);
        JsonNode value = root.get("value");
        if (value != null && value.isArray()) {
            for (JsonNode node : value) {
                String id = node.has("id") ? node.get("id").asText() : null;
                if (id != null && !id.isBlank()) {
                    models.add(new ConnectionTestResponse.ModelInfo(id, id));
                }
            }
        }
        return models;
    }

    private String getDeploymentName(Provider provider) {
        AuthConfig authConfig = provider.getAuthConfig();
        if (authConfig == null || authConfig.getDeploymentName() == null || authConfig.getDeploymentName().isBlank()) {
            throw new IllegalArgumentException("authConfig 缺少字段: deploymentName");
        }
        return authConfig.getDeploymentName();
    }

    private String getAzureApiVersion(Provider provider) {
        AuthConfig authConfig = provider.getAuthConfig();
        if (authConfig == null || authConfig.getAzureApiVersion() == null || authConfig.getAzureApiVersion().isBlank()) {
            throw new IllegalArgumentException("authConfig 缺少字段: azureApiVersion");
        }
        return authConfig.getAzureApiVersion();
    }
}

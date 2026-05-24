package com.hify.modules.provider.service.adapter;

import com.fasterxml.jackson.databind.JsonNode;
import com.hify.modules.provider.dto.chat.ChatRequest;
import com.hify.modules.provider.dto.chat.ChatResponse;
import com.hify.modules.provider.dto.response.ConnectionTestResponse;
import com.hify.modules.provider.dto.vo.AuthConfig;
import com.hify.modules.provider.client.LlmHttpClient;
import com.hify.modules.provider.entity.Provider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Anthropic 适配器
 * <p>
 * 端点：GET /v1/models<br>
 * 鉴权：x-api-key + anthropic-version<br>
 * 响应：{"data": [{"id": "claude-sonnet-4"}, ...]}
 */
@Slf4j
@Component
public class AnthropicAdapter extends AbstractProviderAdapter {

    private static final String DEFAULT_API_VERSION = "2023-06-01";

    public AnthropicAdapter(LlmHttpClient httpClient) {
        super(httpClient);
    }

    @Override
    public List<ConnectionTestResponse.ModelInfo> listModels(Provider provider) throws IOException {
        String baseUrl = provider.getBaseUrl().replaceAll("/+$", "");
        String url = baseUrl + "/v1/models";

        Map<String, String> headers = new HashMap<>();
        String apiKey = provider.getApiKey();
        if (apiKey != null && !apiKey.isBlank()) {
            headers.put("x-api-key", apiKey);
        }

        // 从 authConfig 中提取 apiVersion，使用默认值兜底
        String apiVersion = DEFAULT_API_VERSION;
        Object authConfigObj = provider.getAuthConfig();
        if (authConfigObj instanceof AuthConfig authConfig) {
            if (authConfig.getApiVersion() != null && !authConfig.getApiVersion().isBlank()) {
                apiVersion = authConfig.getApiVersion();
            }
        }
        headers.put("anthropic-version", apiVersion);

        log.debug("Anthropic adapter listing models: {}, version={}", url, apiVersion);
        String responseBody = httpClient.get(url, headers);
        return parseAnthropicModels(responseBody);
    }

    private List<ConnectionTestResponse.ModelInfo> parseAnthropicModels(String responseBody) throws IOException {
        List<ConnectionTestResponse.ModelInfo> models = new ArrayList<>();
        JsonNode root = objectMapper.readTree(responseBody);
        JsonNode data = root.get("data");
        if (data != null && data.isArray()) {
            for (JsonNode node : data) {
                String id = node.has("id") ? node.get("id").asText() : null;
                if (id != null && !id.isBlank()) {
                    models.add(new ConnectionTestResponse.ModelInfo(id, id));
                }
            }
        }
        return models;
    }

    @Override
    public ChatResponse chat(Provider provider, ChatRequest request) throws IOException {
        throw new UnsupportedOperationException("Anthropic chat not implemented yet");
    }

    @Override
    public void streamChat(Provider provider, ChatRequest request,
                           Consumer<String> onDelta, Consumer<String> onFinish) throws IOException {
        throw new UnsupportedOperationException("Anthropic streamChat not implemented yet");
    }
}

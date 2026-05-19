package com.hify.modules.provider.domain.adapter;

import com.fasterxml.jackson.databind.JsonNode;
import com.hify.modules.provider.api.dto.response.ConnectionTestResult;
import com.hify.modules.provider.infra.client.LlmHttpClient;
import com.hify.modules.provider.infra.entity.Provider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Ollama 适配器
 * <p>
 * 端点：GET /api/tags<br>
 * 鉴权：无<br>
 * 响应：{"models": [{"name": "llama2"}, ...]}
 */
@Slf4j
@Component
public class OllamaAdapter extends AbstractProviderAdapter {

    public OllamaAdapter(LlmHttpClient httpClient) {
        super(httpClient);
    }

    @Override
    public List<ConnectionTestResult.ModelInfo> listModels(Provider provider) throws IOException {
        String baseUrl = provider.getBaseUrl().replaceAll("/+$", "");
        String url = baseUrl + "/api/tags";

        log.debug("Ollama adapter listing models: {}", url);
        String responseBody = httpClient.get(url, null);
        return parseOllamaModels(responseBody);
    }

    private List<ConnectionTestResult.ModelInfo> parseOllamaModels(String responseBody) throws IOException {
        List<ConnectionTestResult.ModelInfo> models = new ArrayList<>();
        JsonNode root = objectMapper.readTree(responseBody);
        JsonNode arr = root.get("models");
        if (arr != null && arr.isArray()) {
            for (JsonNode node : arr) {
                String name = node.has("name") ? node.get("name").asText() : null;
                if (name != null && !name.isBlank()) {
                    models.add(new ConnectionTestResult.ModelInfo(name, name));
                }
            }
        }
        return models;
    }
}

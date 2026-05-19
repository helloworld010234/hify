package com.hify.modules.provider.domain.adapter;

import com.fasterxml.jackson.databind.JsonNode;
import com.hify.modules.provider.api.dto.response.ConnectionTestResult;
import com.hify.modules.provider.infra.client.LlmHttpClient;
import com.hify.modules.provider.infra.entity.Provider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * OpenAI 原生适配器
 * <p>
 * 端点：GET /v1/models<br>
 * 鉴权：Bearer Token<br>
 * 响应：{"data": [{"id": "gpt-4o"}, ...]}
 */
@Slf4j
@Component
public class OpenAiAdapter extends AbstractProviderAdapter {

    public OpenAiAdapter(LlmHttpClient httpClient) {
        super(httpClient);
    }

    @Override
    public List<ConnectionTestResult.ModelInfo> listModels(Provider provider) throws IOException {
        String baseUrl = provider.getBaseUrl().replaceAll("/+$", "");
        String url = baseUrl + "/v1/models";

        Map<String, String> headers = new HashMap<>();
        String apiKey = provider.getApiKey();
        if (apiKey != null && !apiKey.isBlank()) {
            headers.put("Authorization", "Bearer " + apiKey);
        }

        log.debug("OpenAI adapter listing models: {}", url);
        String responseBody = httpClient.get(url, headers);
        return parseOpenAiModels(responseBody);
    }

    protected List<ConnectionTestResult.ModelInfo> parseOpenAiModels(String responseBody) throws IOException {
        List<ConnectionTestResult.ModelInfo> models = new ArrayList<>();
        JsonNode root = objectMapper.readTree(responseBody);
        JsonNode data = root.get("data");
        if (data != null && data.isArray()) {
            for (JsonNode node : data) {
                String id = node.has("id") ? node.get("id").asText() : null;
                if (id != null && !id.isBlank()) {
                    models.add(new ConnectionTestResult.ModelInfo(id, id));
                }
            }
        }
        return models;
    }
}

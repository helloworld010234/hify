package com.hify.modules.provider.service.adapter;

import com.fasterxml.jackson.databind.JsonNode;
import com.hify.modules.provider.dto.chat.ChatRequest;
import com.hify.modules.provider.dto.chat.ChatResponse;
import com.hify.modules.provider.dto.response.ConnectionTestResponse;
import com.hify.modules.provider.client.LlmHttpClient;
import com.hify.modules.provider.entity.Provider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

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
    public List<ConnectionTestResponse.ModelInfo> listModels(Provider provider) throws IOException {
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

    protected List<ConnectionTestResponse.ModelInfo> parseOpenAiModels(String responseBody) throws IOException {
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
        String baseUrl = provider.getBaseUrl().replaceAll("/+$", "");
        String url = baseUrl + "/v1/chat/completions";

        Map<String, String> headers = buildAuthHeaders(provider);
        String jsonBody = buildChatRequestBody(request, false);

        String responseBody = httpClient.post(url, headers, jsonBody);
        return parseChatResponse(responseBody);
    }

    @Override
    public void streamChat(Provider provider, ChatRequest request,
                           Consumer<String> onDelta, Consumer<String> onFinish) throws IOException {
        String baseUrl = provider.getBaseUrl().replaceAll("/+$", "");
        String url = baseUrl + "/v1/chat/completions";

        Map<String, String> headers = buildAuthHeaders(provider);
        String jsonBody = buildChatRequestBody(request, true);

        AtomicBoolean finishReceived = new AtomicBoolean(false);
        httpClient.postStream(url, headers, jsonBody, line -> {
            String trimmed = line.trim();
            if (!trimmed.startsWith("data:")) {
                return;
            }
            String jsonStr = trimmed.substring(5).trim();
            if (jsonStr.isEmpty() || "[DONE]".equals(jsonStr)) {
                return;
            }
            try {
                JsonNode root = objectMapper.readTree(jsonStr);
                JsonNode choices = root.get("choices");
                if (choices == null || !choices.isArray() || choices.isEmpty()) {
                    return;
                }
                JsonNode delta = choices.get(0).get("delta");
                if (delta == null) {
                    return;
                }
                String content = delta.has("content") && !delta.get("content").isNull()
                        ? delta.get("content").asText()
                        : null;
                if (content != null && !content.isEmpty()) {
                    onDelta.accept(content);
                }
                String finishReason = choices.get(0).has("finish_reason")
                        ? choices.get(0).get("finish_reason").asText()
                        : null;
                if (finishReason != null && !finishReason.isBlank() && !"null".equals(finishReason)) {
                    finishReceived.set(true);
                    onFinish.accept(finishReason);
                }
            } catch (IOException e) {
                log.warn("Failed to parse SSE line: {}", jsonStr, e);
            }
        });
        if (!finishReceived.get()) {
            onFinish.accept("stop");
        }
    }

    protected Map<String, String> buildAuthHeaders(Provider provider) {
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json");
        String apiKey = provider.getApiKey();
        if (apiKey != null && !apiKey.isBlank()) {
            headers.put("Authorization", "Bearer " + apiKey);
        }
        return headers;
    }

    protected String buildChatRequestBody(ChatRequest request, boolean stream) throws IOException {
        Map<String, Object> body = new HashMap<>();
        body.put("model", request.getModel());
        body.put("stream", stream);

        List<Map<String, Object>> messages = new ArrayList<>();
        if (request.getMessages() != null) {
            for (com.hify.modules.provider.dto.chat.ChatMessage msg : request.getMessages()) {
                Map<String, Object> m = new HashMap<>();
                m.put("role", msg.getRole());
                m.put("content", msg.getContent());
                if (msg.getToolCalls() != null) {
                    m.put("tool_calls", msg.getToolCalls());
                }
                if (msg.getToolCallId() != null) {
                    m.put("tool_call_id", msg.getToolCallId());
                }
                messages.add(m);
            }
        }
        body.put("messages", messages);

        if (request.getTemperature() != null) {
            body.put("temperature", request.getTemperature());
        }
        if (request.getMaxTokens() != null) {
            body.put("max_tokens", request.getMaxTokens());
        }
        if (request.getTools() != null && !request.getTools().isEmpty()) {
            body.put("tools", request.getTools());
        }
        return objectMapper.writeValueAsString(body);
    }

    protected ChatResponse parseChatResponse(String responseBody) throws IOException {
        JsonNode root = objectMapper.readTree(responseBody);
        ChatResponse response = new ChatResponse();
        response.setId(root.has("id") ? root.get("id").asText() : null);
        response.setModel(root.has("model") ? root.get("model").asText() : null);

        JsonNode choices = root.get("choices");
        if (choices != null && choices.isArray() && !choices.isEmpty()) {
            JsonNode first = choices.get(0);
            response.setFinishReason(first.has("finish_reason") ? first.get("finish_reason").asText() : null);
            JsonNode message = first.get("message");
            if (message != null) {
                if (message.has("content") && !message.get("content").isNull()) {
                    response.setContent(message.get("content").asText());
                }
                // 解析 tool_calls
                JsonNode toolCallsNode = message.get("tool_calls");
                if (toolCallsNode != null && toolCallsNode.isArray()) {
                    List<Map<String, Object>> toolCalls = new ArrayList<>();
                    for (JsonNode tc : toolCallsNode) {
                        toolCalls.add(objectMapper.convertValue(tc, Map.class));
                    }
                    response.setToolCalls(toolCalls);
                }
            }
        }

        JsonNode usage = root.get("usage");
        if (usage != null) {
            com.hify.modules.provider.dto.chat.Usage u = new com.hify.modules.provider.dto.chat.Usage();
            u.setPromptTokens(usage.has("prompt_tokens") ? usage.get("prompt_tokens").asInt() : null);
            u.setCompletionTokens(usage.has("completion_tokens") ? usage.get("completion_tokens").asInt() : null);
            u.setTotalTokens(usage.has("total_tokens") ? usage.get("total_tokens").asInt() : null);
            response.setUsage(u);
        }
        return response;
    }
}

package com.hify.modules.knowledge.infra.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Embedding API 客户端
 * <p>
 * 调用 OpenAI 兼容的 Embedding 接口，将文本批量转换为向量。
 * 配置（baseUrl、apiKey、model）由调用方传入，不复用 Spring @Value 注入。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EmbeddingClient {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    private static final int MAX_BATCH_SIZE = 100;

    /**
     * 批量嵌入文本
     *
     * @param texts   文本列表
     * @param baseUrl API Base URL，如 https://api.openai.com/v1
     * @param apiKey  API Key
     * @param model   模型名称，如 text-embedding-3-small
     * @return 向量列表（与输入顺序一致）
     */
    public List<float[]> embedBatch(List<String> texts, String baseUrl, String apiKey, String model) {
        if (texts == null || texts.isEmpty()) {
            return List.of();
        }

        List<float[]> allEmbeddings = new ArrayList<>();

        for (int i = 0; i < texts.size(); i += MAX_BATCH_SIZE) {
            List<String> batch = texts.subList(i, Math.min(i + MAX_BATCH_SIZE, texts.size()));
            List<float[]> batchEmbeddings = doEmbedBatch(batch, baseUrl, apiKey, model);
            allEmbeddings.addAll(batchEmbeddings);
        }

        return allEmbeddings;
    }

    private List<float[]> doEmbedBatch(List<String> texts, String baseUrl, String apiKey, String model) {
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("model", model);
            body.put("input", texts);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);

            String url = baseUrl.endsWith("/") ? baseUrl + "embeddings" : baseUrl + "/embeddings";
            ResponseEntity<String> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    new HttpEntity<>(body, headers),
                    String.class
            );

            JsonNode root = objectMapper.readTree(response.getBody());
            JsonNode data = root.path("data");

            // API 返回的 data[] 按 index 字段排序，再按原始顺序对应
            Map<Integer, float[]> indexToEmbedding = new HashMap<>();
            for (JsonNode item : data) {
                int index = item.path("index").asInt();
                JsonNode embeddingNode = item.path("embedding");
                float[] vec = new float[embeddingNode.size()];
                for (int i = 0; i < embeddingNode.size(); i++) {
                    vec[i] = embeddingNode.get(i).floatValue();
                }
                indexToEmbedding.put(index, vec);
            }

            // 按输入顺序重组
            List<float[]> embeddings = new ArrayList<>();
            for (int i = 0; i < texts.size(); i++) {
                float[] vec = indexToEmbedding.get(i);
                if (vec == null) {
                    throw new RuntimeException("Embedding 返回缺少 index=" + i);
                }
                embeddings.add(vec);
            }

            return embeddings;

        } catch (Exception e) {
            log.error("Embedding batch failed, textsCount={}", texts.size(), e);
            throw new RuntimeException("Embedding 调用失败: " + e.getMessage(), e);
        }
    }
}

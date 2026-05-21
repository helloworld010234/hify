package com.hify.modules.knowledge.infra.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Embedding API 客户端
 * <p>
 * 支持两种调用方式：
 * <ul>
 *   <li>OpenAI 兼容接口（text-embedding-v3/v4 等纯文本模型）</li>
 *   <li>DashScope 原生多模态接口（tongyi-embedding-vision-*、qwen*-vl-embedding 等）</li>
 * </ul>
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
     * @param baseUrl API Base URL，如 https://dashscope.aliyuncs.com/compatible-mode/v1
     * @param apiKey  API Key
     * @param model   模型名称，如 text-embedding-v4 或 tongyi-embedding-vision-flash
     * @return 向量列表（与输入顺序一致）
     */
    public List<float[]> embedBatch(List<String> texts, String baseUrl, String apiKey, String model) {
        if (texts == null || texts.isEmpty()) {
            return List.of();
        }

        List<float[]> allEmbeddings = new ArrayList<>();

        for (int i = 0; i < texts.size(); i += MAX_BATCH_SIZE) {
            List<String> batch = texts.subList(i, Math.min(i + MAX_BATCH_SIZE, texts.size()));
            List<float[]> batchEmbeddings = isMultimodalModel(model)
                    ? doEmbedBatchMultimodal(batch, baseUrl, apiKey, model)
                    : doEmbedBatchOpenAI(batch, baseUrl, apiKey, model);
            allEmbeddings.addAll(batchEmbeddings);
        }

        return allEmbeddings;
    }

    // ---------- OpenAI 兼容接口（纯文本模型） ----------

    private List<float[]> doEmbedBatchOpenAI(List<String> texts, String baseUrl, String apiKey, String model) {
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("model", model);
            body.put("input", texts);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);

            String url = baseUrl.replaceAll("/+$", "") + "/v1/embeddings";
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
            log.error("Embedding batch (OpenAI) failed, textsCount={}", texts.size(), e);
            throw new RuntimeException("Embedding 调用失败: " + e.getMessage(), e);
        }
    }

    // ---------- DashScope 原生多模态接口 ----------

    private List<float[]> doEmbedBatchMultimodal(List<String> texts, String baseUrl, String apiKey, String model) {
        try {
            // 构造原生多模态 API URL：提取 host，拼接固定 path
            String url = buildMultimodalUrl(baseUrl);

            // 构造 input.contents，每个文本一个 {"text": "..."}
            List<Map<String, String>> contents = new ArrayList<>();
            for (String text : texts) {
                Map<String, String> item = new HashMap<>();
                item.put("text", text);
                contents.add(item);
            }

            Map<String, Object> input = new HashMap<>();
            input.put("contents", contents);

            Map<String, Object> body = new HashMap<>();
            body.put("model", model);
            body.put("input", input);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);

            ResponseEntity<String> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    new HttpEntity<>(body, headers),
                    String.class
            );

            JsonNode root = objectMapper.readTree(response.getBody());
            JsonNode embeddingsNode = root.path("output").path("embeddings");

            // 多模态 API 按 contents 顺序返回，无 index 字段
            List<float[]> embeddings = new ArrayList<>();
            for (JsonNode item : embeddingsNode) {
                JsonNode embeddingNode = item.path("embedding");
                float[] vec = new float[embeddingNode.size()];
                for (int i = 0; i < embeddingNode.size(); i++) {
                    vec[i] = embeddingNode.get(i).floatValue();
                }
                embeddings.add(vec);
            }

            if (embeddings.size() != texts.size()) {
                throw new RuntimeException(
                        "Embedding 返回数量不匹配: expected=" + texts.size() + ", actual=" + embeddings.size());
            }

            return embeddings;

        } catch (Exception e) {
            log.error("Embedding batch (Multimodal) failed, textsCount={}", texts.size(), e);
            throw new RuntimeException("Embedding 调用失败: " + e.getMessage(), e);
        }
    }

    /**
     * 从 OpenAI 兼容 baseUrl 推导出 DashScope 原生多模态 API URL
     * <p>
     * 例如：
     * https://dashscope.aliyuncs.com/compatible-mode/v1
     * → https://dashscope.aliyuncs.com/api/v1/services/embeddings/multimodal-embedding/multimodal-embedding
     */
    private String buildMultimodalUrl(String baseUrl) {
        URI uri = URI.create(baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl);
        String scheme = uri.getScheme();
        String host = uri.getHost();
        int port = uri.getPort();
        String authority = host + (port != -1 ? ":" + port : "");
        return scheme + "://" + authority
                + "/api/v1/services/embeddings/multimodal-embedding/multimodal-embedding";
    }

    /**
     * 判断是否为 DashScope 多模态 Embedding 模型（需使用原生 API）
     */
    private boolean isMultimodalModel(String model) {
        if (model == null) {
            return false;
        }
        String lower = model.toLowerCase();
        return lower.contains("tongyi-embedding-vision")
                || lower.contains("qwen") && lower.contains("vl") && lower.contains("embedding")
                || lower.contains("multimodal-embedding");
    }
}

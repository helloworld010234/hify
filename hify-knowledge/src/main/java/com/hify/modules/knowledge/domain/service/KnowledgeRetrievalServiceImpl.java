package com.hify.modules.knowledge.domain.service;

import com.hify.modules.knowledge.api.KnowledgeRetrievalService;
import com.hify.modules.knowledge.infra.client.EmbeddingClient;
import com.hify.modules.knowledge.infra.pg.DocumentChunk;
import com.hify.modules.knowledge.infra.pg.DocumentChunkRepository;
import com.hify.modules.provider.api.ProviderService;
import com.hify.modules.provider.api.dto.response.ProviderDetailResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 知识库检索服务实现（RAG）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeRetrievalServiceImpl implements KnowledgeRetrievalService {

    private final EmbeddingClient embeddingClient;
    private final DocumentChunkRepository documentChunkRepository;
    private final ProviderService providerService;

    @Value("${hify.rag.embedding.provider-id:}")
    private Long embeddingProviderId;

    @Value("${hify.rag.embedding.model:text-embedding-v4}")
    private String embeddingModel;

    @Override
    public List<String> retrieve(Long knowledgeBaseId, String query, int topK, double minSimilarity) {
        if (knowledgeBaseId == null || query == null || query.isBlank()) {
            return List.of();
        }

        // 1. 获取 Embedding Provider 配置
        if (embeddingProviderId == null) {
            log.warn("Embedding Provider ID 未配置，跳过知识库检索");
            return List.of();
        }
        ProviderDetailResponse provider = providerService.getById(embeddingProviderId);
        if (provider == null) {
            log.warn("Embedding Provider 不存在: id={}", embeddingProviderId);
            return List.of();
        }
        String baseUrl = provider.getBaseUrl();
        String apiKey = providerService.getApiKey(embeddingProviderId);
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("Embedding Provider API Key 为空，跳过知识库检索");
            return List.of();
        }

        // 2. 向量化查询文本
        List<float[]> embeddings;
        try {
            embeddings = embeddingClient.embedBatch(List.of(query), baseUrl, apiKey, embeddingModel);
        } catch (Exception e) {
            log.error("查询文本向量化失败", e);
            return List.of();
        }
        if (embeddings.isEmpty()) {
            return List.of();
        }
        float[] embedding = embeddings.get(0);

        // 3. 构造向量字符串
        String embeddingStr = floatArrayToVectorString(embedding);

        // 4. 相似度检索
        List<DocumentChunk> chunks;
        try {
            chunks = documentChunkRepository.searchByKnowledgeBase(knowledgeBaseId, embeddingStr, topK);
        } catch (Exception e) {
            log.error("知识库相似度检索失败: kbId={}", knowledgeBaseId, e);
            return List.of();
        }

        // 5. 过滤并返回内容
        chunks.forEach(c -> log.info("RAG chunk similarity: chunkIndex={}, similarity={}", c.getChunkIndex(), c.getSimilarity()));
        return chunks.stream()
                .filter(c -> c.getSimilarity() != null && c.getSimilarity() >= minSimilarity)
                .map(DocumentChunk::getContent)
                .toList();
    }

    private String floatArrayToVectorString(float[] vector) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) {
                sb.append(",");
            }
            sb.append(vector[i]);
        }
        sb.append("]");
        return sb.toString();
    }
}

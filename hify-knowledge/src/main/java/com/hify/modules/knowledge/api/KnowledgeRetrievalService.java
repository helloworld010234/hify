package com.hify.modules.knowledge.api;

import java.util.List;

/**
 * 知识库检索服务（RAG）
 * <p>
 * 供其他模块调用，根据用户查询检索相关知识库分块。
 */
public interface KnowledgeRetrievalService {

    /**
     * 检索与查询文本最相关的知识库分块
     *
     * @param knowledgeBaseId 知识库 ID
     * @param query           用户查询文本
     * @param topK            返回条数上限
     * @param minSimilarity   最小相似度阈值（0~1）
     * @return 相关分块内容列表（已按相似度排序）
     */
    List<String> retrieve(Long knowledgeBaseId, String query, int topK, double minSimilarity);
}

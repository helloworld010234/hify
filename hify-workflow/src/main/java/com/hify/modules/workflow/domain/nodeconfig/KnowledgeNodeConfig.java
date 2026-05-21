package com.hify.modules.workflow.domain.nodeconfig;

/**
 * 知识库检索节点配置。
 *
 * @param knowledgeBaseId 知识库 ID（t_knowledge_base.id）
 * @param query           查询语句模板，支持 {{variable}} 占位符
 * @param topK            召回 Top-K 片段
 * @param outputVariable  输出变量名（将检索结果写入上下文）
 */
public record KnowledgeNodeConfig(Long knowledgeBaseId, String query, Integer topK,
                                  String outputVariable)
    implements NodeConfig {
}

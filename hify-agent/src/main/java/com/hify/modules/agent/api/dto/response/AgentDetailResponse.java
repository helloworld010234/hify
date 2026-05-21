package com.hify.modules.agent.api.dto.response;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Agent 详情响应
 */
@Data
public class AgentDetailResponse {

    private Long id;
    private String name;
    private String description;
    private String systemPrompt;

    /**
     * 绑定的模型配置 ID
     */
    private Long modelConfigId;

    /**
     * 绑定的知识库 ID
     */
    private Long knowledgeBaseId;

    /**
     * 模型显示名称
     */
    private String modelName;

    private BigDecimal temperature;
    private Integer maxTokens;
    private Integer maxContextTurns;
    private Integer enabled;

    /**
     * 关联的知识库 ID 列表
     */
    private List<Long> knowledgeIds;

    /**
     * 关联的工具 ID 列表
     */
    private List<Long> toolIds;

    /**
     * 绑定的工作流 ID
     */
    private Long workflowId;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /**
     * 静态工厂方法：从 Entity 和关联数据组装响应
     */
    public static AgentDetailResponse from(com.hify.modules.agent.infra.entity.Agent agent,
                                            String modelName,
                                            List<Long> knowledgeIds,
                                            List<Long> toolIds) {
        AgentDetailResponse resp = new AgentDetailResponse();
        resp.setId(agent.getId());
        resp.setName(agent.getName());
        resp.setDescription(agent.getDescription());
        resp.setSystemPrompt(agent.getSystemPrompt());
        resp.setModelConfigId(agent.getModelConfigId());
        resp.setKnowledgeBaseId(agent.getKnowledgeBaseId());
        resp.setModelName(modelName);
        resp.setTemperature(agent.getTemperature());
        resp.setMaxTokens(agent.getMaxTokens());
        resp.setMaxContextTurns(agent.getMaxContextTurns());
        resp.setEnabled(agent.getEnabled());
        resp.setKnowledgeIds(knowledgeIds);
        resp.setToolIds(toolIds);
        resp.setWorkflowId(agent.getWorkflowId());
        resp.setCreatedAt(agent.getCreatedAt());
        resp.setUpdatedAt(agent.getUpdatedAt());
        return resp;
    }
}

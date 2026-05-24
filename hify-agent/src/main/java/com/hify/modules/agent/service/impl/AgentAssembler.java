package com.hify.modules.agent.service.impl;

import com.hify.modules.agent.dto.response.AgentDetailResponse;
import com.hify.modules.agent.entity.Agent;

import java.util.List;

/**
 * Agent 对象转换器：Entity + 关联数据 → Response DTO。
 */
public class AgentAssembler {

    public static AgentDetailResponse toDetailResponse(Agent agent,
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
        resp.setCreatedAt(agent.getCreatedAt());
        resp.setUpdatedAt(agent.getUpdatedAt());
        return resp;
    }
}

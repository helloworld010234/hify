package com.hify.modules.agent.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hify.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Agent-知识库关联实体（对应 t_agent_knowledge_rel 表）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_agent_knowledge_rel")
public class AgentKnowledgeRel extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /**
     * Agent ID（t_agent.id）
     */
    private Long agentId;

    /**
     * 知识库 ID（t_knowledge.id）
     */
    private Long knowledgeId;
}

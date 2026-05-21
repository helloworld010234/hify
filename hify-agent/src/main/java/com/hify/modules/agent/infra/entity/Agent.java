package com.hify.modules.agent.infra.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hify.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * Agent 实体（对应 t_agent 表）
 * <p>
 * 继承 {@link BaseEntity}，统一包含 id、createdAt、updatedAt、deleted。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_agent")
public class Agent extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /**
     * Agent 名称（唯一）
     */
    private String name;

    /**
     * Agent 描述
     */
    private String description;

    /**
     * 角色指令 / 系统提示词
     */
    private String systemPrompt;

    /**
     * 绑定的模型配置 ID（t_model.id）
     */
    private Long modelConfigId;

    /**
     * 绑定的知识库 ID（t_knowledge_base.id）
     */
    private Long knowledgeBaseId;

    /**
     * 温度（0.00 ~ 1.00）
     */
    private BigDecimal temperature;

    /**
     * 最大输出 token 数
     */
    private Integer maxTokens;

    /**
     * 保留最近几轮上下文
     */
    private Integer maxContextTurns;

    /**
     * 是否启用：1-启用 0-禁用
     */
    private Integer enabled;
}

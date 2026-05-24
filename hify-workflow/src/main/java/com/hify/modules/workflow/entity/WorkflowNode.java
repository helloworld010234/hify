package com.hify.modules.workflow.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hify.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 工作流节点定义实体（对应 t_workflow_node 表）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_workflow_node")
public class WorkflowNode extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /**
     * 所属工作流 ID
     */
    private Long workflowId;

    /**
     * 节点业务标识（如 "intent-judge"）
     */
    private String nodeKey;

    /**
     * 节点类型（START / LLM / CONDITION / API_CALL / KNOWLEDGE / END）
     */
    private String nodeType;

    /**
     * 节点展示名称
     */
    private String name;

    /**
     * 节点配置 JSON 字符串
     */
    private String configJson;
}

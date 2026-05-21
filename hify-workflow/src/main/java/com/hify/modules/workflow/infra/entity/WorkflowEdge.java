package com.hify.modules.workflow.infra.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hify.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 工作流节点连接关系实体（对应 t_workflow_edge 表）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_workflow_edge")
public class WorkflowEdge extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /**
     * 所属工作流 ID
     */
    private Long workflowId;

    /**
     * 源节点标识
     */
    private String sourceNodeKey;

    /**
     * 目标节点标识
     */
    private String targetNodeKey;

    /**
     * 条件表达式（条件分支节点出边使用）
     */
    private String conditionExpr;

    /**
     * 同 source 的多条边排序
     */
    private Integer sortOrder;
}

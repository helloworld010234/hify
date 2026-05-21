package com.hify.modules.workflow.infra.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hify.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 工作流节点执行记录实体（对应 t_workflow_node_run 表）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_workflow_node_run")
public class WorkflowNodeRun extends BaseEntity {

    private static final long serialVersionUID = 1L;

    private Long workflowRunId;
    private String nodeKey;
    private String nodeType;
    private String status;
    private String outputs;
    private String error;
    private Integer elapsedMs;
    private LocalDateTime finishedAt;
}

package com.hify.modules.workflow.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hify.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 工作流执行记录实体（对应 t_workflow_run 表）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_workflow_run")
public class WorkflowRun extends BaseEntity {

    private static final long serialVersionUID = 1L;

    private Long workflowId;
    private String status;
    private String input;
    private String output;
    private String error;
    private Integer elapsedMs;
    private LocalDateTime finishedAt;
}

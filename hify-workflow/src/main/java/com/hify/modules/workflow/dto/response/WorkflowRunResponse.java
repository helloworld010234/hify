package com.hify.modules.workflow.dto.response;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 工作流执行结果响应
 */
@Data
public class WorkflowRunResponse {

    private Long runId;
    private String status;
    private String output;
    private String error;
    private Integer elapsedMs;
    private LocalDateTime createdAt;
    private LocalDateTime finishedAt;
}

package com.hify.modules.workflow.api.dto.response;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 工作流列表项响应
 */
@Data
public class WorkflowListResponse {

    private Long id;

    private String name;

    private String description;

    private Integer enabled;

    private Integer nodeCount;

    private Integer edgeCount;

    private LocalDateTime createdAt;
}

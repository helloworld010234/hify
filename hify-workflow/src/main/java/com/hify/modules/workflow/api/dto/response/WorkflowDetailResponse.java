package com.hify.modules.workflow.api.dto.response;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 工作流详情响应（含完整节点和边）
 */
@Data
public class WorkflowDetailResponse {

    private Long id;

    private String name;

    private String description;

    private Integer enabled;

    private List<NodeResponse> nodes;

    private List<EdgeResponse> edges;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}

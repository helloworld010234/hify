package com.hify.modules.workflow.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 更新工作流请求
 */
@Data
public class WorkflowUpdateRequest {

    @NotBlank(message = "工作流名称不能为空")
    @Size(max = 100, message = "名称最多 100 个字符")
    private String name;

    @Size(max = 500, message = "描述最多 500 个字符")
    private String description = "";

    private Integer enabled = 1;

    @Valid
    private List<NodeRequest> nodes;

    @Valid
    private List<EdgeRequest> edges;
}

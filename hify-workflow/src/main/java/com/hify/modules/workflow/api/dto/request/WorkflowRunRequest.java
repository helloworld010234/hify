package com.hify.modules.workflow.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 执行工作流请求
 */
@Data
public class WorkflowRunRequest {

    @NotBlank(message = "用户输入消息不能为空")
    private String userMessage;
}

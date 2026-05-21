package com.hify.modules.workflow.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 工作流节点连接关系请求（创建 / 更新时使用）
 */
@Data
public class EdgeRequest {

    @NotBlank(message = "源节点标识不能为空")
    private String sourceNodeKey;

    @NotBlank(message = "目标节点标识不能为空")
    private String targetNodeKey;

    @com.fasterxml.jackson.annotation.JsonProperty("condition")
    private String conditionExpr;

    private Integer sortOrder;
}

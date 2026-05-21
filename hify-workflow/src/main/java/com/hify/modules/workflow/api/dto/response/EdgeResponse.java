package com.hify.modules.workflow.api.dto.response;

import lombok.Data;

/**
 * 工作流节点连接关系响应
 */
@Data
public class EdgeResponse {

    private String sourceNodeKey;

    private String targetNodeKey;

    private String conditionExpr;

    private Integer sortOrder;
}

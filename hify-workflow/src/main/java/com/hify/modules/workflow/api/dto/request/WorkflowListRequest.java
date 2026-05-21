package com.hify.modules.workflow.api.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * 工作流列表查询请求
 */
@Data
public class WorkflowListRequest {

    @JsonProperty("page")
    private Integer current = 1;

    @JsonProperty("size")
    private Integer size = 20;

    private String keyword;

    private Integer enabled;
}

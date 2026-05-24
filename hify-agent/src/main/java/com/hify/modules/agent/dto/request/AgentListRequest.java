package com.hify.modules.agent.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * Agent 列表查询请求
 */
@Data
public class AgentListRequest {

    /**
     * 当前页码
     */
    @JsonProperty("page")
    private Integer current = 1;

    /**
     * 每页大小
     */
    @JsonProperty("size")
    private Integer size = 20;

    /**
     * 按名称模糊搜索
     */
    private String keyword;

    /**
     * 按启用状态筛选：1-启用 0-禁用
     */
    private Integer enabled;

    /**
     * 按模型配置筛选
     */
    private Long modelConfigId;
}

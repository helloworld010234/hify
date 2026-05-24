package com.hify.modules.mcp.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * MCP Server 列表查询请求
 */
@Data
public class McpServerListRequest {

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
     * 按连通状态筛选：connected / disconnected / unknown
     */
    private String status;

    /**
     * 按名称模糊搜索
     */
    private String keyword;
}

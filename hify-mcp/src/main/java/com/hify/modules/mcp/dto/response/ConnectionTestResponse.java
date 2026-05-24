package com.hify.modules.mcp.dto.response;

import lombok.Data;

/**
 * MCP Server 连通性测试结果
 */
@Data
public class ConnectionTestResponse {

    /**
     * 是否成功
     */
    private boolean success;

    /**
     * 响应耗时（毫秒）
     */
    private Long latencyMs;

    /**
     * 工具数量
     */
    private Integer toolCount;

    /**
     * 错误信息（失败时）
     */
    private String errorMessage;
}

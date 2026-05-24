package com.hify.modules.mcp.dto.response;

import lombok.Data;

/**
 * MCP 工具调试响应
 */
@Data
public class McpDebugResponse {

    /**
     * 是否调用成功
     */
    private boolean success;

    /**
     * 工具返回文本
     */
    private String result;

    /**
     * 错误信息（调用失败时）
     */
    private String errorMessage;

    /**
     * 调用耗时（毫秒）
     */
    private long latencyMs;

    public static McpDebugResponse ok(String result, long latencyMs) {
        McpDebugResponse resp = new McpDebugResponse();
        resp.setSuccess(true);
        resp.setResult(result);
        resp.setLatencyMs(latencyMs);
        return resp;
    }

    public static McpDebugResponse fail(String errorMessage, long latencyMs) {
        McpDebugResponse resp = new McpDebugResponse();
        resp.setSuccess(false);
        resp.setErrorMessage(errorMessage);
        resp.setLatencyMs(latencyMs);
        return resp;
    }
}

package com.hify.modules.mcp.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.Map;

/**
 * MCP 工具调试请求
 */
@Data
public class McpDebugRequest {

    /**
     * 工具名称
     */
    @NotBlank(message = "工具名称不能为空")
    private String toolName;

    /**
     * 调用参数（key-value 形式）
     */
    private Map<String, Object> arguments;
}

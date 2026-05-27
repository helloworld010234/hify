package com.hify.modules.mcp.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 更新 MCP Server 请求
 */
@Data
public class McpServerUpdateRequest {

    @NotBlank(message = "名称不能为空")
    private String name;

    @NotBlank(message = "端点地址不能为空")
    private String endpoint;

    /**
     * 是否启用：true-启用 false-禁用
     */
    private Boolean enabled;
}

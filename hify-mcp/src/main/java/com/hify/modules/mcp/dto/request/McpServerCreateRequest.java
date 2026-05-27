package com.hify.modules.mcp.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 创建 MCP Server 请求
 */
@Data
public class McpServerCreateRequest {

    @NotBlank(message = "名称不能为空")
    private String name;

    @NotBlank(message = "端点地址不能为空")
    private String endpoint;

    /**
     * 是否启用：true-启用 false-禁用，默认启用
     */
    private Boolean enabled = true;
}

package com.hify.modules.mcp.dto.response;


import lombok.Data;

/**
 * MCP Tool 响应
 */
@Data
public class McpToolResponse {

    private Long id;
    private String name;
    private String description;
    private String inputSchema;
}

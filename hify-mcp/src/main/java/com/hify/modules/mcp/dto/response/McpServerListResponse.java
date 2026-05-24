package com.hify.modules.mcp.dto.response;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * MCP Server 列表项响应
 */
@Data
public class McpServerListResponse {

    private Long id;
    private String name;
    private String endpoint;
    private Integer enabled;
    private String status;
    private Integer toolCount;
    private LocalDateTime lastCheckTime;
    private String lastErrorMsg;
    private LocalDateTime createdAt;
}

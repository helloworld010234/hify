package com.hify.modules.mcp.dto.response;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * MCP Server 详情响应
 */
@Data
public class McpServerDetailResponse {

    private Long id;
    private String name;
    private String endpoint;
    private Integer enabled;
    private String status;
    private Integer toolCount;
    private LocalDateTime lastCheckTime;
    private String lastErrorMsg;
    private LocalDateTime createdAt;

    /**
     * 工具列表
     */
    private List<McpToolResponse> tools;
}

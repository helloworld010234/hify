package com.hify.common.service.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;

/**
 * MCP 工具定义（跨模块传输，不含数据库实体细节）
 */
@Data
public class McpToolDefinition {

    private Long id;
    private Long serverId;
    private String name;
    private String description;
    private JsonNode inputSchema;
}

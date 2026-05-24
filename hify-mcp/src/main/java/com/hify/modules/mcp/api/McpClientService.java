package com.hify.modules.mcp.api;

import java.util.List;
import java.util.Map;

/**
 * MCP Client 调用 Service 接口
 * <p>
 * 供其他模块（如 Chat、Agent）调用，执行远端 MCP 工具。
 */
public interface McpClientService {

    /**
     * 调用指定 MCP Server 上的工具
     *
     * @param mcpServerId MCP Server ID
     * @param toolName    工具名称
     * @param arguments   调用参数
     * @return 工具返回文本（多条 TextContent 用换行拼接）
     * @throws com.hify.common.exception.BizException MCP_TOOL_CALL_FAILED
     */
    String callTool(Long mcpServerId, String toolName, Map<String, Object> arguments);

    /**
     * 列出指定 MCP Server 上的所有工具名称
     *
     * @param mcpServerId MCP Server ID
     * @return 工具名称列表
     * @throws com.hify.common.exception.BizException MCP_SERVER_NOT_FOUND
     */
    List<String> listTools(Long mcpServerId);
}

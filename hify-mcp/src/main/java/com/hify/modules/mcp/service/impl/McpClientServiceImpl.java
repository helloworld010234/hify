package com.hify.modules.mcp.service.impl;

import com.hify.common.exception.BizException;
import com.hify.common.exception.ErrorCode;
import com.hify.modules.mcp.api.McpClientService;
import com.hify.modules.mcp.entity.McpServer;
import com.hify.modules.mcp.mapper.McpServerMapper;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * MCP Client 调用 Service 实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class McpClientServiceImpl implements com.hify.modules.mcp.api.McpClientService {

    private final McpServerMapper mcpServerMapper;

    @Override
    public String callTool(Long mcpServerId, String toolName, Map<String, Object> arguments) {
        McpServer server = mcpServerMapper.selectById(mcpServerId);
        if (server == null || server.getDeleted() != null && server.getDeleted() == 1) {
            throw new BizException(ErrorCode.MCP_SERVER_NOT_FOUND, "MCP Server 不存在: " + mcpServerId);
        }

        try (McpClientResource client = createClient(server)) {
            client.getClient().initialize();
            McpSchema.CallToolResult result = client.getClient().callTool(new McpSchema.CallToolRequest(toolName, arguments));
            return extractText(result);
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.error("MCP tool call failed: serverId={}, toolName={}", mcpServerId, toolName, e);
            throw new BizException(ErrorCode.MCP_TOOL_CALL_FAILED, "工具调用失败: " + e.getMessage(), e);
        }
    }

    @Override
    public List<String> listTools(Long mcpServerId) {
        McpServer server = mcpServerMapper.selectById(mcpServerId);
        if (server == null || server.getDeleted() != null && server.getDeleted() == 1) {
            throw new BizException(ErrorCode.MCP_SERVER_NOT_FOUND, "MCP Server 不存在: " + mcpServerId);
        }

        try (McpClientResource client = createClient(server)) {
            client.getClient().initialize();
            McpSchema.ListToolsResult result = client.getClient().listTools();
            if (result.tools() == null) {
                return List.of();
            }
            return result.tools().stream()
                    .map(McpSchema.Tool::name)
                    .collect(Collectors.toList());
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.error("MCP list tools failed: serverId={}", mcpServerId, e);
            throw new BizException(ErrorCode.MCP_SERVER_NOT_FOUND, "查询工具列表失败: " + e.getMessage(), e);
        }
    }

    private McpClientResource createClient(McpServer server) {
        McpSyncClient client = McpClient.sync(
                HttpClientStreamableHttpTransport.builder(server.getEndpoint()).build()
        ).build();
        return new McpClientResource(client);
    }

    private String extractText(McpSchema.CallToolResult result) {
        if (result.content() == null || result.content().isEmpty()) {
            return "";
        }
        return result.content().stream()
                .filter(c -> c instanceof McpSchema.TextContent)
                .map(c -> ((McpSchema.TextContent) c).text())
                .collect(Collectors.joining("\n"));
    }

    /**
     * MCP Sync Client 包装器，确保支持 try-with-resources
     */
    private static class McpClientResource implements AutoCloseable {
        private final McpSyncClient client;

        McpClientResource(McpSyncClient client) {
            this.client = client;
        }

        McpSyncClient getClient() {
            return client;
        }

        @Override
        public void close() {
            if (client != null) {
                client.close();
            }
        }
    }

}

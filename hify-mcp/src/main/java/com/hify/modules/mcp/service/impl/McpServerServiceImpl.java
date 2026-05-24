package com.hify.modules.mcp.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hify.common.exception.BizException;
import com.hify.common.exception.ErrorCode;
import com.hify.common.controller.PageResult;
import com.hify.common.controller.Result;
import com.hify.common.service.agent.AgentBindingApi;
import com.hify.modules.mcp.service.McpServerService;
import com.hify.modules.mcp.api.McpClientService;
import com.hify.modules.mcp.dto.request.McpDebugRequest;
import com.hify.modules.mcp.dto.request.McpServerCreateRequest;
import com.hify.modules.mcp.dto.request.McpServerListRequest;
import com.hify.modules.mcp.dto.request.McpServerUpdateRequest;
import com.hify.modules.mcp.dto.response.ConnectionTestResponse;
import com.hify.modules.mcp.dto.response.McpDebugResponse;
import com.hify.modules.mcp.dto.response.McpServerDetailResponse;
import com.hify.modules.mcp.dto.response.McpServerListResponse;
import com.hify.modules.mcp.dto.response.McpToolResponse;
import com.hify.modules.mcp.entity.McpServer;
import com.hify.modules.mcp.entity.McpTool;
import com.hify.modules.mcp.mapper.McpServerMapper;
import com.hify.modules.mcp.mapper.McpToolMapper;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * MCP Server 管理 Service 实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class McpServerServiceImpl implements McpServerService {

    private final McpServerMapper mcpServerMapper;
    private final McpToolMapper mcpToolMapper;
    private final AgentBindingApi agentBindingApi;
    private final ObjectMapper objectMapper;
    private final McpClientService mcpClientService;

    // ==================== 创建 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(McpServerCreateRequest request) {
        // 校验名称唯一性
        if (mcpServerMapper.selectByName(request.getName()) != null) {
            throw new BizException(ErrorCode.PARAM_ERROR, "MCP Server 名称已存在: " + request.getName());
        }

        McpServer server = new McpServer();
        server.setName(request.getName());
        server.setEndpoint(request.getEndpoint());
        server.setEnabled(request.getEnabled() != null ? request.getEnabled() : 1);
        server.setStatus("unknown");
        server.setToolCount(0);

        mcpServerMapper.insert(server);
        log.info("MCP Server created: id={}, name={}", server.getId(), server.getName());
        return server.getId();
    }

    // ==================== 更新 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, McpServerUpdateRequest request) {
        McpServer server = mcpServerMapper.selectById(id);
        if (server == null || server.getDeleted() != null && server.getDeleted() == 1) {
            throw new BizException(ErrorCode.MCP_SERVER_NOT_FOUND, "MCP Server 不存在: " + id);
        }

        // 校验名称唯一性（排除自身）
        McpServer exist = mcpServerMapper.selectByName(request.getName());
        if (exist != null && !exist.getId().equals(id)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "MCP Server 名称已存在: " + request.getName());
        }

        server.setName(request.getName());
        server.setEndpoint(request.getEndpoint());
        if (request.getEnabled() != null) {
            server.setEnabled(request.getEnabled());
        }

        mcpServerMapper.updateById(server);
        log.info("MCP Server updated: id={}", id);
    }

    // ==================== 删除 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        McpServer server = mcpServerMapper.selectById(id);
        if (server == null || server.getDeleted() != null && server.getDeleted() == 1) {
            throw new BizException(ErrorCode.MCP_SERVER_NOT_FOUND, "MCP Server 不存在: " + id);
        }

        // 检查是否有 Agent 绑定了该 Server 的工具
        List<Long> toolIds = mcpToolMapper.selectIdsByServerId(id);
        if (!toolIds.isEmpty() && agentBindingApi.hasAgentBoundToTools(toolIds)) {
            throw new BizException(ErrorCode.MCP_SERVER_BOUND_BY_AGENT);
        }

        // 逻辑删除 Server 及其工具
        mcpServerMapper.deleteById(id);
        mcpToolMapper.delete(
                new LambdaQueryWrapper<McpTool>()
                        .eq(McpTool::getServerId, id)
        );
        log.info("MCP Server deleted: id={}, name={}", id, server.getName());
    }

    // ==================== 详情 ====================

    @Override
    public McpServerDetailResponse getById(Long id) {
        McpServer server = mcpServerMapper.selectById(id);
        if (server == null || server.getDeleted() != null && server.getDeleted() == 1) {
            throw new BizException(ErrorCode.MCP_SERVER_NOT_FOUND, "MCP Server 不存在: " + id);
        }

        McpServerDetailResponse response = new McpServerDetailResponse();
        response.setId(server.getId());
        response.setName(server.getName());
        response.setEndpoint(server.getEndpoint());
        response.setEnabled(server.getEnabled());
        response.setStatus(server.getStatus());
        response.setToolCount(server.getToolCount());
        response.setLastCheckTime(server.getLastCheckTime());
        response.setLastErrorMsg(server.getLastErrorMsg());
        response.setCreatedAt(server.getCreatedAt());

        // 查询工具列表
        List<McpTool> tools = mcpToolMapper.selectByServerId(id);
        response.setTools(tools.stream().map(this::convertToToolResponse).toList());

        return response;
    }

    // ==================== 列表 ====================

    @Override
    public Result<PageResult<McpServerListResponse>> list(McpServerListRequest request) {
        LambdaQueryWrapper<McpServer> wrapper = new LambdaQueryWrapper<McpServer>()
                .eq(McpServer::getDeleted, 0);

        if (org.springframework.util.StringUtils.hasText(request.getKeyword())) {
            wrapper.like(McpServer::getName, request.getKeyword());
        }
        if (org.springframework.util.StringUtils.hasText(request.getStatus())) {
            wrapper.eq(McpServer::getStatus, request.getStatus());
        }

        wrapper.orderByDesc(McpServer::getCreatedAt);

        Page<McpServer> page = mcpServerMapper.selectPage(
                new Page<>(request.getCurrent(), request.getSize()),
                wrapper
        );

        List<McpServerListResponse> records = page.getRecords().stream()
                .map(this::convertToListResponse)
                .toList();

        return PageResult.of(records, page.getTotal(), (int) page.getCurrent(), (int) page.getSize());
    }

    // ==================== 连通性测试 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ConnectionTestResponse testConnection(Long id) {
        McpServer server = mcpServerMapper.selectById(id);
        if (server == null || server.getDeleted() != null && server.getDeleted() == 1) {
            throw new BizException(ErrorCode.MCP_SERVER_NOT_FOUND, "MCP Server 不存在: " + id);
        }

        ConnectionTestResponse result = new ConnectionTestResponse();
        long start = System.currentTimeMillis();

        try (McpClientResource client = createClient(server)) {
            client.getClient().initialize();
            McpSchema.ListToolsResult toolsResult = client.getClient().listTools();

            long latency = System.currentTimeMillis() - start;
            int toolCount = toolsResult.tools() != null ? toolsResult.tools().size() : 0;

            result.setSuccess(true);
            result.setLatencyMs(latency);
            result.setToolCount(toolCount);

            // 同步工具列表到 mcp_tool 表
            syncTools(id, toolsResult.tools());

            // 更新 Server 状态
            server.setStatus("connected");
            server.setToolCount(toolCount);
            server.setLastCheckTime(LocalDateTime.now());
            server.setLastErrorMsg(null);
            mcpServerMapper.updateById(server);

            log.info("MCP Server connection tested: id={}, name={}, success=true, latency={}ms, tools={}",
                    id, server.getName(), latency, toolCount);
        } catch (Exception e) {
            long latency = System.currentTimeMillis() - start;
            result.setSuccess(false);
            result.setLatencyMs(latency);
            result.setErrorMessage(e.getMessage());

            server.setStatus("disconnected");
            server.setLastCheckTime(LocalDateTime.now());
            server.setLastErrorMsg(e.getMessage());
            mcpServerMapper.updateById(server);

            log.warn("MCP Server connection test failed: id={}, name={}, error={}",
                    id, server.getName(), e.getMessage());
        }

        return result;
    }

    // ==================== 调试 ====================

    @Override
    public McpDebugResponse debug(Long id, McpDebugRequest request) {
        McpServer server = mcpServerMapper.selectById(id);
        if (server == null || server.getDeleted() != null && server.getDeleted() == 1) {
            throw new BizException(ErrorCode.MCP_SERVER_NOT_FOUND, "MCP Server 不存在: " + id);
        }

        long start = System.currentTimeMillis();
        try {
            String result = mcpClientService.callTool(id, request.getToolName(),
                    request.getArguments() != null ? request.getArguments() : java.util.Map.of());
            long latency = System.currentTimeMillis() - start;
            return McpDebugResponse.ok(result, latency);
        } catch (BizException e) {
            long latency = System.currentTimeMillis() - start;
            return McpDebugResponse.fail(e.getMessage(), latency);
        } catch (Exception e) {
            long latency = System.currentTimeMillis() - start;
            log.error("MCP debug failed: serverId={}, toolName={}", id, request.getToolName(), e);
            return McpDebugResponse.fail("工具调用失败: " + e.getMessage(), latency);
        }
    }

    // ==================== 私有方法 ====================

    private McpClientResource createClient(McpServer server) {
        McpSyncClient client = McpClient.sync(
                HttpClientStreamableHttpTransport.builder(server.getEndpoint()).build()
        ).build();
        return new McpClientResource(client);
    }

    private void syncTools(Long serverId, List<McpSchema.Tool> tools) {
        // 软删除旧工具
        mcpToolMapper.delete(
                new LambdaQueryWrapper<McpTool>()
                        .eq(McpTool::getServerId, serverId)
        );

        if (tools == null || tools.isEmpty()) {
            return;
        }

        for (McpSchema.Tool tool : tools) {
            McpTool mcpTool = new McpTool();
            mcpTool.setServerId(serverId);
            mcpTool.setName(tool.name());
            mcpTool.setDescription(tool.description());

            Object schema = tool.inputSchema();
            if (schema != null) {
                try {
                    mcpTool.setInputSchema(objectMapper.writeValueAsString(schema));
                } catch (Exception e) {
                    log.warn("Failed to serialize inputSchema for tool: {}", tool.name(), e);
                }
            }

            mcpToolMapper.insert(mcpTool);
        }
    }

    private McpServerListResponse convertToListResponse(McpServer server) {
        McpServerListResponse resp = new McpServerListResponse();
        resp.setId(server.getId());
        resp.setName(server.getName());
        resp.setEndpoint(server.getEndpoint());
        resp.setEnabled(server.getEnabled());
        resp.setStatus(server.getStatus());
        resp.setToolCount(server.getToolCount());
        resp.setLastCheckTime(server.getLastCheckTime());
        resp.setLastErrorMsg(server.getLastErrorMsg());
        resp.setCreatedAt(server.getCreatedAt());
        return resp;
    }

    private McpToolResponse convertToToolResponse(McpTool tool) {
        McpToolResponse resp = new McpToolResponse();
        resp.setId(tool.getId());
        resp.setName(tool.getName());
        resp.setDescription(tool.getDescription());
        resp.setInputSchema(tool.getInputSchema());
        return resp;
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

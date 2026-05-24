package com.hify.modules.mcp.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hify.common.service.mcp.McpToolService;
import com.hify.common.service.mcp.McpToolDefinition;
import com.hify.modules.mcp.entity.McpServer;
import com.hify.modules.mcp.entity.McpTool;
import com.hify.modules.mcp.mapper.McpServerMapper;
import com.hify.modules.mcp.mapper.McpToolMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * MCP Tool 查询 Service 实现
 */
@Service
@RequiredArgsConstructor
public class McpToolServiceImpl implements McpToolService {

    private final McpToolMapper mcpToolMapper;
    private final McpServerMapper mcpServerMapper;
    private final ObjectMapper objectMapper;

    @Override
    public List<Long> findInvalidToolIds(List<Long> toolIds) {
        if (toolIds == null || toolIds.isEmpty()) {
            return Collections.emptyList();
        }

        List<McpTool> tools = mcpToolMapper.selectList(
                new LambdaQueryWrapper<McpTool>()
                        .in(McpTool::getId, toolIds)
                        .eq(McpTool::getDeleted, 0)
        );

        // 找出不存在的 toolId
        List<Long> existingIds = tools.stream().map(McpTool::getId).toList();
        List<Long> invalidIds = toolIds.stream()
                .filter(id -> !existingIds.contains(id))
                .collect(Collectors.toList());

        // 检查所属 Server 是否启用
        List<Long> serverIds = tools.stream()
                .map(McpTool::getServerId)
                .distinct()
                .toList();

        if (!serverIds.isEmpty()) {
            List<McpServer> servers = mcpServerMapper.selectList(
                    new LambdaQueryWrapper<McpServer>()
                            .in(McpServer::getId, serverIds)
                            .eq(McpServer::getDeleted, 0)
            );
            Map<Long, McpServer> serverMap = servers.stream()
                    .collect(Collectors.toMap(McpServer::getId, s -> s));

            for (McpTool tool : tools) {
                McpServer server = serverMap.get(tool.getServerId());
                if (server == null || server.getEnabled() == null || server.getEnabled() == 0) {
                    invalidIds.add(tool.getId());
                }
            }
        }

        return invalidIds;
    }

    @Override
    public Map<Long, String> getToolNames(List<Long> toolIds) {
        if (toolIds == null || toolIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<McpTool> tools = mcpToolMapper.selectList(
                new LambdaQueryWrapper<McpTool>()
                        .in(McpTool::getId, toolIds)
                        .eq(McpTool::getDeleted, 0)
        );
        return tools.stream()
                .collect(Collectors.toMap(McpTool::getId, McpTool::getName));
    }

    @Override
    public List<McpToolDefinition> getToolDefinitions(List<Long> toolIds) {
        if (toolIds == null || toolIds.isEmpty()) {
            return Collections.emptyList();
        }
        List<McpTool> tools = mcpToolMapper.selectList(
                new LambdaQueryWrapper<McpTool>()
                        .in(McpTool::getId, toolIds)
                        .eq(McpTool::getDeleted, 0)
        );
        return tools.stream().map(this::convertToDefinition).collect(Collectors.toList());
    }

    @Override
    public List<McpToolDefinition> listAllTools() {
        List<McpTool> tools = mcpToolMapper.selectList(
                new LambdaQueryWrapper<McpTool>()
                        .eq(McpTool::getDeleted, 0)
        );
        return tools.stream().map(this::convertToDefinition).collect(Collectors.toList());
    }

    private McpToolDefinition convertToDefinition(McpTool tool) {
        McpToolDefinition def = new McpToolDefinition();
        def.setId(tool.getId());
        def.setServerId(tool.getServerId());
        def.setName(tool.getName());
        def.setDescription(tool.getDescription());
        try { def.setInputSchema(objectMapper.readTree(tool.getInputSchema())); } catch (Exception e) { def.setInputSchema(null); }
        return def;
    }
}

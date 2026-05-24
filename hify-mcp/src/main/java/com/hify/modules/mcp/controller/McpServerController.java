package com.hify.modules.mcp.controller;

import com.hify.common.controller.PageResult;
import com.hify.common.controller.Result;
import com.hify.modules.mcp.service.McpServerService;
import com.hify.modules.mcp.dto.request.McpDebugRequest;
import com.hify.modules.mcp.dto.request.McpServerCreateRequest;
import com.hify.modules.mcp.dto.request.McpServerListRequest;
import com.hify.modules.mcp.dto.request.McpServerUpdateRequest;
import com.hify.modules.mcp.dto.response.ConnectionTestResponse;
import com.hify.modules.mcp.dto.response.McpDebugResponse;
import com.hify.modules.mcp.dto.response.McpServerDetailResponse;
import com.hify.modules.mcp.dto.response.McpServerListResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * MCP Server 管理 Controller
 */
@RestController
@RequestMapping("/api/v1/mcp-servers")
@RequiredArgsConstructor
public class McpServerController {

    private final McpServerService mcpServerService;

    /**
     * 创建 MCP Server
     */
    @PostMapping
    public Result<Long> create(@Valid @RequestBody McpServerCreateRequest request) {
        Long id = mcpServerService.create(request);
        return Result.ok(id);
    }

    /**
     * 分页查询 MCP Server 列表
     */
    @GetMapping
    public Result<PageResult<McpServerListResponse>> list(McpServerListRequest request) {
        return mcpServerService.list(request);
    }

    /**
     * 获取 MCP Server 详情（含工具列表）
     */
    @GetMapping("/{id}")
    public Result<McpServerDetailResponse> getById(@PathVariable Long id) {
        McpServerDetailResponse detail = mcpServerService.getById(id);
        return Result.ok(detail);
    }

    /**
     * 更新 MCP Server
     */
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id,
                               @Valid @RequestBody McpServerUpdateRequest request) {
        mcpServerService.update(id, request);
        return Result.ok();
    }

    /**
     * 删除 MCP Server（逻辑删除）
     */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        mcpServerService.delete(id);
        return Result.ok();
    }

    /**
     * 连通性测试
     */
    @PostMapping("/{id}/test")
    public Result<ConnectionTestResponse> testConnection(@PathVariable Long id) {
        ConnectionTestResponse result = mcpServerService.testConnection(id);
        return Result.ok(result);
    }

    /**
     * 调试调用指定工具
     */
    @PostMapping("/{id}/debug")
    public Result<McpDebugResponse> debug(@PathVariable Long id,
                                          @Valid @RequestBody McpDebugRequest request) {
        McpDebugResponse result = mcpServerService.debug(id, request);
        return Result.ok(result);
    }
}

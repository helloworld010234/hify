package com.hify.modules.mcp.service;

import com.hify.common.controller.PageResult;
import com.hify.common.controller.Result;
import com.hify.modules.mcp.dto.request.McpServerCreateRequest;
import com.hify.modules.mcp.dto.request.McpServerListRequest;
import com.hify.modules.mcp.dto.request.McpServerUpdateRequest;
import com.hify.modules.mcp.dto.request.McpDebugRequest;
import com.hify.modules.mcp.dto.response.ConnectionTestResponse;
import com.hify.modules.mcp.dto.response.McpDebugResponse;
import com.hify.modules.mcp.dto.response.McpServerDetailResponse;
import com.hify.modules.mcp.dto.response.McpServerListResponse;

/**
 * MCP Server 管理 Service 接口
 * <p>
 * 供本模块 Controller 和其他模块调用。
 */
public interface McpServerService {

    /**
     * 创建 MCP Server
     *
     * @param request 创建请求
     * @return 新建 Server ID
     */
    Long create(McpServerCreateRequest request);

    /**
     * 更新 MCP Server
     *
     * @param id      Server ID
     * @param request 更新请求
     */
    void update(Long id, McpServerUpdateRequest request);

    /**
     * 删除 MCP Server（逻辑删除）
     * <p>
     * 若该 Server 下的工具已被 Agent 绑定，则拒绝删除。
     *
     * @param id Server ID
     */
    void delete(Long id);

    /**
     * 获取 MCP Server 详情（含工具列表）
     *
     * @param id Server ID
     * @return 详情响应
     */
    McpServerDetailResponse getById(Long id);

    /**
     * 分页查询 MCP Server 列表
     *
     * @param request 列表查询条件
     * @return 分页结果
     */
    Result<PageResult<McpServerListResponse>> list(McpServerListRequest request);

    /**
     * 连通性测试
     * <p>
     * 调用远端 MCP Server 的 tools/list，成功后同步工具列表到本地。
     *
     * @param id Server ID
     * @return 测试结果
     */
    ConnectionTestResponse testConnection(Long id);

    /**
     * 调试调用指定 MCP Server 的工具
     *
     * @param id      Server ID
     * @param request 调试请求
     * @return 调试结果
     */
    McpDebugResponse debug(Long id, McpDebugRequest request);
}

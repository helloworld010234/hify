package com.hify.common.service.mcp;

import com.hify.common.service.mcp.McpToolDefinition;

import java.util.List;
import java.util.Map;

/**
 * MCP Tool 查询 Service 接口
 * <p>
 * 供其他模块（如 Agent、Chat）跨模块调用，校验工具有效性、获取工具定义。
 */
public interface McpToolService {

    /**
     * 校验工具 ID 列表，返回无效的工具 ID（不存在或所属 Server 未启用）
     *
     * @param toolIds 工具 ID 列表
     * @return 无效工具 ID 列表，全部有效返回空列表
     */
    List<Long> findInvalidToolIds(List<Long> toolIds);

    /**
     * 批量查询工具名称
     *
     * @param toolIds 工具 ID 列表
     * @return key=toolId, value=toolName
     */
    Map<Long, String> getToolNames(List<Long> toolIds);

    /**
     * 批量查询工具定义（用于构造 LLM tools 参数）
     *
     * @param toolIds 工具 ID 列表
     * @return 工具定义列表
     */
    List<McpToolDefinition> getToolDefinitions(List<Long> toolIds);

    /**
     * 查询所有可用工具定义
     *
     * @return 所有未删除的工具定义列表
     */
    List<McpToolDefinition> listAllTools();
}

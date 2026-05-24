package com.hify.modules.agent.api;

import com.hify.common.controller.PageResult;
import com.hify.common.controller.Result;
import com.hify.modules.agent.dto.request.AgentCreateRequest;
import com.hify.modules.agent.dto.request.AgentListRequest;
import com.hify.modules.agent.dto.request.AgentUpdateRequest;
import com.hify.modules.agent.dto.response.AgentDetailResponse;
import com.hify.modules.agent.dto.response.AgentListResponse;
import com.hify.modules.agent.dto.response.ModelGroupResponse;
import com.hify.modules.agent.dto.response.ToolOptionResponse;

import java.util.List;

/**
 * Agent 管理 Service 接口
 */
public interface AgentService {

    /**
     * 创建 Agent
     */
    AgentDetailResponse create(AgentCreateRequest request);

    /**
     * 更新 Agent
     */
    void update(Long id, AgentUpdateRequest request);

    /**
     * 删除 Agent（逻辑删除 + 清理关联）
     */
    void delete(Long id);

    /**
     * 获取 Agent 详情（含关联数据）
     */
    AgentDetailResponse getById(Long id);

    /**
     * 分页查询 Agent 列表
     */
    Result<PageResult<AgentListResponse>> list(AgentListRequest request);

    /**
     * 克隆 Agent
     */
    Long clone(Long id);

    /**
     * 获取所有可用模型（按供应商分组）
     */
    List<ModelGroupResponse> listModelGroups();

    /**
     * 获取所有可用工具（用于前端多选绑定）
     */
    List<ToolOptionResponse> listTools();

    /**
     * 快捷修改 Agent 上下文轮数（列表页行内编辑）
     */
    void updateMaxContextTurns(Long id, Integer maxContextTurns);

    /**
     * 快捷修改 Agent 温度（列表页行内编辑）
     */
    void updateTemperature(Long id, java.math.BigDecimal temperature);

    /**
     * 快捷修改 Agent 工具绑定（列表页行内编辑）
     */
    void updateToolIds(Long id, java.util.List<Long> toolIds);
}

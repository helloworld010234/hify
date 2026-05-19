package com.hify.modules.agent.domain.service;

import com.hify.common.web.PageResult;
import com.hify.modules.agent.api.dto.request.AgentCreateRequest;
import com.hify.modules.agent.api.dto.request.AgentListRequest;
import com.hify.modules.agent.api.dto.request.AgentUpdateRequest;
import com.hify.modules.agent.api.dto.response.AgentDetailResponse;
import com.hify.modules.agent.api.dto.response.AgentListResponse;
import com.hify.modules.agent.api.dto.response.ModelGroupResponse;
import com.hify.modules.agent.api.dto.response.ToolOption;

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
    PageResult<AgentListResponse> list(AgentListRequest request);

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
    List<ToolOption> listTools();
}

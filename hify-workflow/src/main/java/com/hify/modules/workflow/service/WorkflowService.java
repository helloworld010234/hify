package com.hify.modules.workflow.service;

import com.hify.common.controller.Result;
import com.hify.common.controller.PageResult;
import com.hify.modules.workflow.dto.request.WorkflowCreateRequest;
import com.hify.modules.workflow.dto.request.WorkflowListRequest;
import com.hify.modules.workflow.dto.request.WorkflowUpdateRequest;
import com.hify.modules.workflow.dto.response.WorkflowDetailResponse;
import com.hify.modules.workflow.dto.response.WorkflowListResponse;

/**
 * 工作流管理 Service 接口
 */
public interface WorkflowService {

    /**
     * 创建工作流（含节点和边）
     */
    WorkflowDetailResponse create(WorkflowCreateRequest request);

    /**
     * 更新工作流（全量替换节点和边）
     */
    void update(Long id, WorkflowUpdateRequest request);

    /**
     * 删除工作流（逻辑删除，级联删除节点和边）
     */
    void delete(Long id);

    /**
     * 查询工作流详情（含完整节点和边）
     */
    WorkflowDetailResponse getById(Long id);

    /**
     * 分页查询工作流列表
     */
    Result<PageResult<WorkflowListResponse>> list(WorkflowListRequest request);
}

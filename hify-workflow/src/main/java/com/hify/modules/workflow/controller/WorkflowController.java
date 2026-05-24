package com.hify.modules.workflow.controller;

import com.hify.common.controller.PageResult;
import com.hify.common.controller.Result;
import com.hify.modules.workflow.dto.request.WorkflowCreateRequest;
import com.hify.modules.workflow.dto.request.WorkflowListRequest;
import com.hify.modules.workflow.dto.request.WorkflowRunRequest;
import com.hify.modules.workflow.dto.request.WorkflowUpdateRequest;
import com.hify.modules.workflow.dto.response.WorkflowDetailResponse;
import com.hify.modules.workflow.dto.response.WorkflowListResponse;
import com.hify.modules.workflow.dto.response.WorkflowRunResponse;
import com.hify.modules.workflow.api.WorkflowRunService;
import com.hify.modules.workflow.service.WorkflowService;
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
 * 工作流管理 Controller
 */
@RestController
@RequestMapping("/api/v1/workflows")
@RequiredArgsConstructor
public class WorkflowController {

    private final WorkflowService workflowService;
    private final WorkflowRunService workflowRunService;

    /**
     * 创建工作流
     */
    @PostMapping
    public Result<WorkflowDetailResponse> create(@Valid @RequestBody WorkflowCreateRequest request) {
        WorkflowDetailResponse detail = workflowService.create(request);
        return Result.ok(detail);
    }

    /**
     * 分页查询工作流列表
     */
    @GetMapping
    public Result<PageResult<WorkflowListResponse>> list(WorkflowListRequest request) {
        return workflowService.list(request);
    }

    /**
     * 查询工作流详情（含完整节点和边）
     */
    @GetMapping("/{id:[0-9]+}")
    public Result<WorkflowDetailResponse> getById(@PathVariable Long id) {
        WorkflowDetailResponse detail = workflowService.getById(id);
        return Result.ok(detail);
    }

    /**
     * 更新工作流
     */
    @PutMapping("/{id:[0-9]+}")
    public Result<Void> update(@PathVariable Long id,
                               @Valid @RequestBody WorkflowUpdateRequest request) {
        workflowService.update(id, request);
        return Result.ok();
    }

    /**
     * 删除工作流（逻辑删除）
     */
    @DeleteMapping("/{id:[0-9]+}")
    public Result<Void> delete(@PathVariable Long id) {
        workflowService.delete(id);
        return Result.ok();
    }

    /**
     * 执行工作流
     */
    @PostMapping("/{id:[0-9]+}/run")
    public Result<WorkflowRunResponse> run(@PathVariable Long id,
                                           @Valid @RequestBody WorkflowRunRequest request) {
        WorkflowRunResponse resp = workflowRunService.runWorkflow(id, request.getUserMessage());
        return Result.ok(resp);
    }
}

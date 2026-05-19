package com.hify.modules.agent.web;

import com.hify.common.web.PageResult;
import com.hify.common.web.Result;
import com.hify.modules.agent.api.dto.request.AgentCreateRequest;
import com.hify.modules.agent.api.dto.request.AgentListRequest;
import com.hify.modules.agent.api.dto.request.AgentUpdateRequest;
import com.hify.modules.agent.api.dto.response.AgentDetailResponse;
import com.hify.modules.agent.api.dto.response.AgentListResponse;
import com.hify.modules.agent.domain.service.AgentService;
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
 * Agent 管理 Controller
 */
@RestController
@RequestMapping("/api/v1/agents")
@RequiredArgsConstructor
public class AgentController {

    private final AgentService agentService;

    /**
     * 创建 Agent
     */
    @PostMapping
    public Result<AgentDetailResponse> create(@Valid @RequestBody AgentCreateRequest request) {
        AgentDetailResponse detail = agentService.create(request);
        return Result.ok(detail);
    }

    /**
     * 分页查询 Agent 列表
     */
    @GetMapping
    public Result<PageResult<AgentListResponse>> list(AgentListRequest request) {
        PageResult<AgentListResponse> pageResult = agentService.list(request);
        return Result.ok(pageResult);
    }

    /**
     * 获取 Agent 详情
     */
    @GetMapping("/{id:[0-9]+}")
    public Result<AgentDetailResponse> getById(@PathVariable Long id) {
        AgentDetailResponse detail = agentService.getById(id);
        return Result.ok(detail);
    }

    /**
     * 更新 Agent
     */
    @PutMapping("/{id:[0-9]+}")
    public Result<Void> update(@PathVariable Long id,
                               @Valid @RequestBody AgentUpdateRequest request) {
        agentService.update(id, request);
        return Result.ok();
    }

    /**
     * 删除 Agent（逻辑删除）
     */
    @DeleteMapping("/{id:[0-9]+}")
    public Result<Void> delete(@PathVariable Long id) {
        agentService.delete(id);
        return Result.ok();
    }

    /**
     * 克隆 Agent
     */
    @PostMapping("/{id:[0-9]+}/clone")
    public Result<Long> clone(@PathVariable Long id) {
        Long newId = agentService.clone(id);
        return Result.ok(newId);
    }

}

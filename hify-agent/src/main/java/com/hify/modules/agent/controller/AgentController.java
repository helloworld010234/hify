package com.hify.modules.agent.controller;

import com.hify.common.controller.PageResult;
import com.hify.common.controller.Result;
import com.hify.modules.agent.dto.request.AgentCreateRequest;
import com.hify.modules.agent.dto.request.AgentListRequest;
import com.hify.modules.agent.dto.request.AgentMaxContextTurnsPatchRequest;
import com.hify.modules.agent.dto.request.AgentTemperaturePatchRequest;
import com.hify.modules.agent.dto.request.AgentToolsPatchRequest;
import com.hify.modules.agent.dto.request.AgentUpdateRequest;
import com.hify.modules.agent.dto.response.AgentDetailResponse;
import com.hify.modules.agent.dto.response.AgentListResponse;
import com.hify.modules.agent.api.AgentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
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
        return agentService.list(request);
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

    /**
     * 快捷修改 Agent 上下文轮数（列表页行内编辑）
     */
    @PatchMapping("/{id:[0-9]+}/max-context-turns")
    public Result<Void> updateMaxContextTurns(@PathVariable Long id,
                                               @Valid @RequestBody AgentMaxContextTurnsPatchRequest request) {
        agentService.updateMaxContextTurns(id, request.getMaxContextTurns());
        return Result.ok();
    }

    /**
     * 快捷修改 Agent 温度（列表页行内编辑）
     */
    @PatchMapping("/{id:[0-9]+}/temperature")
    public Result<Void> updateTemperature(@PathVariable Long id,
                                           @Valid @RequestBody AgentTemperaturePatchRequest request) {
        agentService.updateTemperature(id, request.getTemperature());
        return Result.ok();
    }

    /**
     * 修改 Agent 工具绑定（全量替换）
     */
    @PutMapping("/{id:[0-9]+}/tools")
    public Result<Void> updateTools(@PathVariable Long id,
                                     @Valid @RequestBody AgentToolsPatchRequest request) {
        agentService.updateToolIds(id, request.getToolIds());
        return Result.ok();
    }

}

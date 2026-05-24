package com.hify.modules.agent.controller;

import com.hify.common.controller.Result;
import com.hify.modules.agent.dto.response.ModelGroupResponse;
import com.hify.modules.agent.dto.response.ToolOptionResponse;
import com.hify.modules.agent.api.AgentService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Agent 元数据 Controller
 * <p>
 * 提供模型分组、工具列表等辅助查询接口，
 * 单独拆出避免与 /agents/{id} 的路径冲突。
 */
@RestController
@RequestMapping("/api/v1/agents")
@RequiredArgsConstructor
public class AgentMetaController {

    private final AgentService agentService;

    /**
     * 获取所有可用模型（按供应商分组）
     */
    @GetMapping("/models")
    public Result<List<ModelGroupResponse>> listModelGroups() {
        return Result.ok(agentService.listModelGroups());
    }

    /**
     * 获取所有可用工具
     */
    @GetMapping("/tools")
    public Result<List<ToolOptionResponse>> listTools() {
        return Result.ok(agentService.listTools());
    }
}

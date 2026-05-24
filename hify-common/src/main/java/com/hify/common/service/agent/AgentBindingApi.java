package com.hify.common.service.agent;

import java.util.List;

/**
 * Agent 绑定关系查询接口（供其他模块跨模块调用）
 */
public interface AgentBindingApi {

    /**
     * 检查是否有 Agent 绑定了指定的工具列表
     *
     * @param toolIds 工具 ID 列表
     * @return true-存在绑定，false-无绑定
     */
    boolean hasAgentBoundToTools(List<Long> toolIds);
}

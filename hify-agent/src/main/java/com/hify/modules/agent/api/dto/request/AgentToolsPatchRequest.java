package com.hify.modules.agent.api.dto.request;

import lombok.Data;

import java.util.List;

/**
 * 快捷修改 Agent 工具绑定请求
 */
@Data
public class AgentToolsPatchRequest {

    /**
     * 关联的工具 ID 列表（全量覆盖）
     */
    private List<Long> toolIds;
}

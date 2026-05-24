package com.hify.modules.agent.dto.request;

import jakarta.validation.constraints.Min;
import lombok.Data;

/**
 * 快捷修改 Agent 上下文轮数请求
 */
@Data
public class AgentMaxContextTurnsPatchRequest {

    @Min(value = 1, message = "上下文轮数必须大于 0")
    private Integer maxContextTurns;
}

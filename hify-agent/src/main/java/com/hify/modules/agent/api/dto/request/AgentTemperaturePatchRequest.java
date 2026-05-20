package com.hify.modules.agent.api.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 快捷修改 Agent 温度请求
 */
@Data
public class AgentTemperaturePatchRequest {

    @DecimalMin(value = "0.00", message = "温度不能小于 0")
    @DecimalMax(value = "1.00", message = "温度不能大于 1")
    private BigDecimal temperature;
}

package com.hify.modules.agent.api.dto.request;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 创建 Agent 请求
 */
@Data
public class AgentCreateRequest {

    @NotBlank(message = "Agent 名称不能为空")
    @Size(max = 100, message = "名称最多 100 个字符")
    private String name;

    @Size(max = 500, message = "描述最多 500 个字符")
    private String description = "";

    private String systemPrompt;

    @NotNull(message = "必须绑定一个模型配置")
    private Long modelConfigId;

    @DecimalMin(value = "0.00", message = "温度不能小于 0")
    @DecimalMax(value = "1.00", message = "温度不能大于 1")
    private BigDecimal temperature = new BigDecimal("0.70");

    @Min(value = 1, message = "最大 token 数必须大于 0")
    private Integer maxTokens = 2048;

    @Min(value = 1, message = "上下文轮数必须大于 0")
    private Integer maxContextTurns = 10;

    private Integer enabled = 1;

    /**
     * 关联的知识库 ID 列表
     */
    private List<Long> knowledgeIds;

    /**
     * 关联的工具 ID 列表
     */
    private List<Long> toolIds;
}

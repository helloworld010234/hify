package com.hify.modules.workflow.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.Map;

/**
 * 工作流节点请求（创建 / 更新时使用）
 */
@Data
public class NodeRequest {

    @NotBlank(message = "节点标识不能为空")
    private String nodeKey;

    @NotBlank(message = "节点类型不能为空")
    private String type;

    private String name;

    @NotNull(message = "节点配置不能为空")
    private Map<String, Object> config;
}

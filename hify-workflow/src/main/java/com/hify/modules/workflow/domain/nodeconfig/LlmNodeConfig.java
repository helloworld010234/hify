package com.hify.modules.workflow.domain.nodeconfig;

/**
 * LLM 调用节点配置。
 *
 * @param modelConfigId  模型配置 ID（t_model.id）
 * @param prompt         提示词模板，支持 {{variable}} 占位符
 * @param outputVariable 输出变量名（将 LLM 响应写入上下文）
 */
public record LlmNodeConfig(Long modelConfigId, String prompt, String outputVariable)
    implements NodeConfig {
}

package com.hify.modules.workflow.domain.nodeconfig;

/**
 * 工作流节点配置密封接口。
 * <p>
 * 所有具体节点类型必须实现此接口，并在 {@link NodeConfigParser} 中注册解析逻辑。
 */
public sealed interface NodeConfig
    permits StartNodeConfig, LlmNodeConfig, ConditionNodeConfig,
            ApiCallNodeConfig, KnowledgeNodeConfig, EndNodeConfig {
}

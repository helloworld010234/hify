package com.hify.modules.workflow.domain.nodeconfig;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 工作流节点类型枚举。
 */
@Getter
@RequiredArgsConstructor
public enum NodeConfigType {

    START("START", "开始节点", StartNodeConfig.class),
    LLM("LLM", "LLM 调用节点", LlmNodeConfig.class),
    CONDITION("CONDITION", "条件分支节点", ConditionNodeConfig.class),
    API_CALL("API_CALL", "HTTP 接口调用节点", ApiCallNodeConfig.class),
    KNOWLEDGE("KNOWLEDGE", "知识库检索节点", KnowledgeNodeConfig.class),
    END("END", "结束节点", EndNodeConfig.class);

    private final String code;
    private final String description;
    private final Class<? extends NodeConfig> configClass;

    public static NodeConfigType fromCode(String code) {
        for (NodeConfigType type : values()) {
            if (type.code.equalsIgnoreCase(code)) {
                return type;
            }
        }
        return null;
    }
}

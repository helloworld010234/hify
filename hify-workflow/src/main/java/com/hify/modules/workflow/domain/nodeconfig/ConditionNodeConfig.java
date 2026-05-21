package com.hify.modules.workflow.domain.nodeconfig;

/**
 * 条件分支节点配置。
 *
 * @param expression     条件表达式，支持 {{variable}} 占位符
 * @param outputVariable 输出变量名（将条件结果 true/false 写入上下文）
 */
public record ConditionNodeConfig(String expression, String outputVariable)
    implements NodeConfig {
}

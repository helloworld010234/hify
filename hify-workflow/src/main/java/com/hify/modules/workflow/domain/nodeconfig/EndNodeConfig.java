package com.hify.modules.workflow.domain.nodeconfig;

/**
 * 结束节点配置。
 *
 * @param outputVariable 最终输出变量名（决定工作流返回给调用方的内容）
 */
public record EndNodeConfig(String outputVariable) implements NodeConfig {
}

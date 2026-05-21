package com.hify.modules.workflow.domain.nodeconfig;

import java.util.List;

/**
 * 开始节点配置。
 *
 * @param inputVariables 输入变量列表（定义工作流接收的外部参数）
 */
public record StartNodeConfig(List<InputVariable> inputVariables) implements NodeConfig {

    /**
     * 输入变量定义。
     *
     * @param name        变量名
     * @param type        变量类型（string / number / boolean）
     * @param description 变量描述
     * @param required    是否必填
     */
    public record InputVariable(String name, String type, String description, Boolean required) {
    }
}

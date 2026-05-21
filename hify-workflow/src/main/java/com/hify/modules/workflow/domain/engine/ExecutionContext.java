package com.hify.modules.workflow.domain.engine;

import lombok.Getter;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 工作流执行上下文。
 * <p>
 * 维护节点执行过程中的变量池，支持模板变量替换。
 * 变量命名格式：{@code nodeKey.varName}，如 {@code start.userMessage}、{@code classify.intent}。
 */
public class ExecutionContext {

    @Getter
    private final String workflowRunId;

    private final LinkedHashMap<String, Object> variables = new LinkedHashMap<>();

    /**
     * 构造执行上下文。
     *
     * @param workflowRunId 本次执行唯一标识
     * @param userMessage   用户输入消息，自动预写入为 {@code start.userMessage}
     */
    public ExecutionContext(String workflowRunId, String userMessage) {
        this.workflowRunId = workflowRunId;
        this.variables.put("start.userMessage", userMessage);
    }

    /**
     * 写入变量。
     *
     * @param nodeKey 节点业务标识
     * @param varName 变量名
     * @param value   变量值
     */
    public void set(String nodeKey, String varName, Object value) {
        String key = buildKey(nodeKey, varName);
        this.variables.put(key, value);
    }

    /**
     * 读取变量。
     *
     * @param nodeKey 节点业务标识
     * @param varName 变量名
     * @return 变量值，不存在时返回 {@code null}
     */
    public Object get(String nodeKey, String varName) {
        String key = buildKey(nodeKey, varName);
        return this.variables.get(key);
    }

    /**
     * 解析模板字符串，替换所有 {@code {{nodeKey.varName}}} 占位符。
     * <p>
     * 变量不存在时保留原始占位符，不抛异常。
     *
     * @param template 模板字符串，可包含零个或多个占位符
     * @return 替换后的字符串
     */
    public String resolve(String template) {
        if (template == null || template.isEmpty()) {
            return template;
        }

        String result = template;
        for (Map.Entry<String, Object> entry : variables.entrySet()) {
            String placeholder = "{{" + entry.getKey() + "}}";
            if (result.contains(placeholder)) {
                result = result.replace(placeholder, String.valueOf(entry.getValue()));
            }
        }
        return result;
    }

    /**
     * 返回当前所有变量的只读快照，用于执行记录落库。
     *
     * @return 不可修改的变量视图（保持写入顺序）
     */
    public Map<String, Object> snapshot() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(variables));
    }

    private static String buildKey(String nodeKey, String varName) {
        return nodeKey + "." + varName;
    }
}

package com.hify.modules.workflow.domain.engine.executor;

import com.hify.modules.workflow.domain.engine.ExecutionContext;
import com.hify.modules.workflow.domain.nodeconfig.ConditionNodeConfig;
import com.hify.modules.workflow.domain.nodeconfig.NodeConfig;
import com.hify.modules.workflow.infra.entity.WorkflowNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 条件分支节点执行器。
 */
@Slf4j
@Component
public class ConditionNodeExecutor implements NodeExecutor {

    private static final Pattern COMPARE_PATTERN = Pattern.compile(
            "^\\s*(.+?)\\s*(==|!=)\\s*(.+?)\\s*$"
    );

    @Override
    public String nodeType() {
        return "CONDITION";
    }

    @Override
    public void execute(WorkflowNode node, NodeConfig config, ExecutionContext ctx) throws Exception {
        ConditionNodeConfig cond = (ConditionNodeConfig) config;
        String nodeKey = node.getNodeKey();

        // 1. 替换模板变量
        String resolvedExpr = ctx.resolve(cond.expression());
        log.info("[Workflow][{}] Condition node '{}' expression resolved: {}", ctx.getWorkflowRunId(), nodeKey, resolvedExpr);

        // 2. 求值（支持布尔比较或字符串透传）
        Object result = evaluate(resolvedExpr);
        log.info("[Workflow][{}] Condition node '{}' result: {}", ctx.getWorkflowRunId(), nodeKey, result);

        // 3. 写入上下文
        ctx.set(nodeKey, cond.outputVariable(), result);
    }

    /**
     * 求值条件表达式。
     * <p>
     * 支持格式：
     * <ul>
     *   <li>字面量 {@code true} / {@code false} → Boolean</li>
     *   <li>比较 {@code left == right} 或 {@code left != right} → Boolean</li>
     *   <li>其他纯字符串 → 原样透传（String），用于路由匹配</li>
     * </ul>
     */
    private Object evaluate(String expr) {
        if (expr == null || expr.isBlank()) {
            return false;
        }

        String trimmed = expr.trim();

        // 字面量 true/false
        if ("true".equalsIgnoreCase(trimmed)) {
            return true;
        }
        if ("false".equalsIgnoreCase(trimmed)) {
            return false;
        }

        // 比较运算
        Matcher matcher = COMPARE_PATTERN.matcher(trimmed);
        if (matcher.matches()) {
            String left = stripQuotes(matcher.group(1));
            String operator = matcher.group(2);
            String right = stripQuotes(matcher.group(3));

            boolean equals = left.equals(right);
            return "==".equals(operator) ? equals : !equals;
        }

        // 非比较表达式：字符串透传（如 "售前"）
        return trimmed;
    }

    private String stripQuotes(String s) {
        if (s == null) {
            return "";
        }
        s = s.trim();
        if ((s.startsWith("\"") && s.endsWith("\"")) || (s.startsWith("'") && s.endsWith("'"))) {
            return s.substring(1, s.length() - 1);
        }
        return s;
    }
}

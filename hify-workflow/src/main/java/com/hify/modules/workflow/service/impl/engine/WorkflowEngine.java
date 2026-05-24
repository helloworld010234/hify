package com.hify.modules.workflow.service.impl.engine;

import com.hify.common.exception.BizException;
import com.hify.common.exception.ErrorCode;
import com.hify.modules.workflow.service.impl.engine.executor.NodeExecutor;
import com.hify.modules.workflow.service.impl.engine.executor.NodeExecutorRegistry;
import com.hify.modules.workflow.service.impl.nodeconfig.ConditionNodeConfig;
import com.hify.modules.workflow.service.impl.nodeconfig.NodeConfig;
import com.hify.modules.workflow.service.impl.nodeconfig.NodeConfigParser;
import com.hify.modules.workflow.entity.WorkflowEdge;
import com.hify.modules.workflow.entity.WorkflowNode;
import com.hify.modules.workflow.entity.WorkflowNodeRun;
import com.hify.modules.workflow.entity.WorkflowRun;
import com.hify.modules.workflow.mapper.WorkflowEdgeMapper;
import com.hify.modules.workflow.mapper.WorkflowNodeMapper;
import com.hify.modules.workflow.mapper.WorkflowNodeRunMapper;
import com.hify.modules.workflow.mapper.WorkflowRunMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 工作流执行引擎（第三步：错误处理 + 步数限制 + 落库容错）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WorkflowEngine {

    private static final int MAX_EXECUTION_STEPS = 50;

    private final WorkflowRunMapper runMapper;
    private final WorkflowNodeRunMapper nodeRunMapper;
    private final WorkflowNodeMapper nodeMapper;
    private final WorkflowEdgeMapper edgeMapper;
    private final NodeExecutorRegistry registry;
    private final NodeConfigParser parser;

    /**
     * 执行工作流。
     *
     * @param workflowId  工作流定义 ID
     * @param userMessage 用户输入消息
     * @return 执行结果
     */
    public WorkflowRunResult run(Long workflowId, String userMessage) {
        long globalStart = System.currentTimeMillis();
        WorkflowRun run = createRunRecord(workflowId, userMessage);
        String output = null;
        String error = null;
        int steps = 0;

        try {
            // 加载定义
            List<WorkflowNode> nodes = nodeMapper.selectByWorkflowId(workflowId);
            List<WorkflowEdge> edges = edgeMapper.selectByWorkflowId(workflowId);

            if (nodes.isEmpty()) {
                throw new BizException(ErrorCode.PARAM_ERROR, "工作流没有节点定义");
            }

            Map<String, WorkflowNode> nodeMap = nodes.stream()
                    .collect(Collectors.toMap(WorkflowNode::getNodeKey, n -> n));
            Map<String, List<WorkflowEdge>> edgeMap = edges.stream()
                    .collect(Collectors.groupingBy(WorkflowEdge::getSourceNodeKey));

            // 初始化上下文
            ExecutionContext ctx = new ExecutionContext(String.valueOf(run.getId()), userMessage);

            // 找到 START 节点
            String currentKey = findStartNodeKey(nodeMap);

            // 遍历执行
            while (currentKey != null) {
                // 步数保护
                if (++steps > MAX_EXECUTION_STEPS) {
                    throw new BizException(ErrorCode.PARAM_ERROR,
                            "执行步数超过 " + MAX_EXECUTION_STEPS + " 步，已终止（可能配置错误导致死循环）");
                }

                WorkflowNode node = nodeMap.get(currentKey);
                if (node == null) {
                    throw new BizException(ErrorCode.PARAM_ERROR,
                            "目标节点不存在: " + currentKey);
                }

                executeNode(node, ctx, run.getId());

                // END 节点结束循环
                if ("END".equals(node.getNodeType())) {
                    break;
                }

                currentKey = findNextNode(node, edgeMap, ctx);
            }

            // 收集输出
            output = extractOutput(nodeMap, ctx);
            run.setStatus("SUCCESS");
        } catch (Exception e) {
            run.setStatus("FAILED");
            error = e.getMessage();
            run.setError(error);
            log.error("[Workflow] Execution failed: runId={}, workflowId={}", run.getId(), workflowId, e);
        } finally {
            run.setOutput(output);
            run.setElapsedMs((int) (System.currentTimeMillis() - globalStart));
            run.setFinishedAt(LocalDateTime.now());
            updateRunRecord(run);
        }

        return new WorkflowRunResult(run.getId(), run.getStatus(), output, error);
    }

    // ---------- 私有方法 ----------

    private WorkflowRun createRunRecord(Long workflowId, String input) {
        WorkflowRun run = new WorkflowRun();
        run.setWorkflowId(workflowId);
        run.setStatus("RUNNING");
        run.setInput(input);
        try {
            runMapper.insert(run);
        } catch (Exception e) {
            log.error("[Workflow] Failed to create run record, using fallback UUID", e);
            // 落库失败不阻塞主流程，用 UUID 作为备用 ID
            run.setId(Math.abs(UUID.randomUUID().getMostSignificantBits()));
        }
        return run;
    }

    private void updateRunRecord(WorkflowRun run) {
        try {
            runMapper.updateById(run);
        } catch (Exception e) {
            log.error("[Workflow] Failed to update run record: runId={}", run.getId(), e);
        }
    }

    private String findStartNodeKey(Map<String, WorkflowNode> nodeMap) {
        return nodeMap.values().stream()
                .filter(n -> "START".equals(n.getNodeType()))
                .map(WorkflowNode::getNodeKey)
                .findFirst()
                .orElseThrow(() -> new BizException(ErrorCode.PARAM_ERROR, "工作流缺少 START 节点"));
    }

    private void executeNode(WorkflowNode node, ExecutionContext ctx, Long runId) {
        WorkflowNodeRun nodeRun = new WorkflowNodeRun();
        nodeRun.setWorkflowRunId(runId);
        nodeRun.setNodeKey(node.getNodeKey());
        nodeRun.setNodeType(node.getNodeType());
        nodeRun.setStatus("RUNNING");
        try {
            nodeRunMapper.insert(nodeRun);
        } catch (Exception e) {
            log.error("[Workflow] Failed to insert node run record: runId={}, nodeKey={}", runId, node.getNodeKey(), e);
        }

        long start = System.currentTimeMillis();
        try {
            NodeConfig config = parser.parse(node.getNodeType(), node.getConfigJson());
            NodeExecutor executor = registry.get(node.getNodeType());
            executor.execute(node, config, ctx);

            nodeRun.setStatus("SUCCESS");
        } catch (Exception e) {
            nodeRun.setStatus("FAILED");
            nodeRun.setError(e.getMessage());
            log.error("[Workflow] Node execution failed: runId={}, nodeKey={}", runId, node.getNodeKey(), e);
            // 更新节点记录后抛异常，让外层标记整个工作流失败
            finishNodeRun(nodeRun, start, ctx);
            throw new BizException(ErrorCode.INTERNAL_ERROR,
                    "节点执行失败 [" + node.getNodeKey() + "]: " + e.getMessage());
        }
        finishNodeRun(nodeRun, start, ctx);
    }

    private void finishNodeRun(WorkflowNodeRun nodeRun, long startTime, ExecutionContext ctx) {
        try {
            nodeRun.setOutputs(ctx.snapshot().toString());
        } catch (Exception e) {
            nodeRun.setOutputs("{}");
        }
        nodeRun.setElapsedMs((int) (System.currentTimeMillis() - startTime));
        nodeRun.setFinishedAt(LocalDateTime.now());
        try {
            nodeRunMapper.updateById(nodeRun);
        } catch (Exception e) {
            log.error("[Workflow] Failed to update node run record: runId={}, nodeKey={}",
                    nodeRun.getWorkflowRunId(), nodeRun.getNodeKey(), e);
        }
    }

    // 由于 finishNodeRun 需要 snapshot，我调整 executeNode 直接传 snapshot
    // 但代码已经比较复杂，让我用一个更简洁的方案

    /**
     * 查找下一个节点。
     * <p>
     * CONDITION 节点：从 ctx 取布尔结果，匹配 conditionExpr = "true"/"false" 的边。
     * 其他节点：先找无条件边，没有就取第一条。
     */
    private String findNextNode(WorkflowNode currentNode, Map<String, List<WorkflowEdge>> edgeMap,
                                ExecutionContext ctx) {
        String currentNodeKey = currentNode.getNodeKey();
        List<WorkflowEdge> outEdges = edgeMap.getOrDefault(currentNodeKey, Collections.emptyList());
        if (outEdges.isEmpty()) {
            return null;
        }
        outEdges.sort(Comparator.comparingInt(WorkflowEdge::getSortOrder));

        if ("CONDITION".equals(currentNode.getNodeType())) {
            String matchKey = resolveConditionMatchKey(currentNode, ctx);
            log.info("[Workflow] CONDITION node '{}' matchKey='{}', routing...", currentNodeKey, matchKey);

            for (WorkflowEdge edge : outEdges) {
                String condition = edge.getConditionExpr();
                if (condition != null && !condition.isEmpty() && matchKey.equals(condition)) {
                    return edge.getTargetNodeKey();
                }
            }
            return null;
        }

        // 非 CONDITION 节点：先找无条件边
        for (WorkflowEdge edge : outEdges) {
            if (edge.getConditionExpr() == null || edge.getConditionExpr().isEmpty()) {
                return edge.getTargetNodeKey();
            }
        }
        // 没有无条件边，取第一条
        return outEdges.get(0).getTargetNodeKey();
    }

    private String resolveConditionMatchKey(WorkflowNode node, ExecutionContext ctx) {
        try {
            ConditionNodeConfig cond = (ConditionNodeConfig) parser.parse(node.getNodeType(), node.getConfigJson());
            Object value = ctx.get(node.getNodeKey(), cond.outputVariable());
            if (value instanceof Boolean) {
                return Boolean.TRUE.equals(value) ? "true" : "false";
            }
            if (value instanceof String) {
                String s = (String) value;
                // 若字符串本身是 true/false，保持兼容；否则直接透传
                return "true".equalsIgnoreCase(s) || "false".equalsIgnoreCase(s) ? s.toLowerCase() : s;
            }
            return value != null ? value.toString() : "";
        } catch (Exception e) {
            log.warn("[Workflow] Failed to resolve condition match key for node: {}", node.getNodeKey(), e);
            return "";
        }
    }

    private String extractOutput(Map<String, WorkflowNode> nodeMap, ExecutionContext ctx) {
        for (WorkflowNode node : nodeMap.values()) {
            if ("END".equals(node.getNodeType())) {
                Object val = ctx.get(node.getNodeKey(), "outputVariable");
                if (val != null) {
                    return val.toString();
                }
            }
        }
        Object userMsg = ctx.get("start", "userMessage");
        return userMsg != null ? userMsg.toString() : "";
    }
}

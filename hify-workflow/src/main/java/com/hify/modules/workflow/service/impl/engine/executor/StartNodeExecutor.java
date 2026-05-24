package com.hify.modules.workflow.service.impl.engine.executor;

import com.hify.modules.workflow.service.impl.engine.ExecutionContext;
import com.hify.modules.workflow.service.impl.nodeconfig.NodeConfig;
import com.hify.modules.workflow.service.impl.nodeconfig.StartNodeConfig;
import com.hify.modules.workflow.entity.WorkflowNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 开始节点执行器。
 * <p>
 * START 节点无需额外处理，userMessage 已在 ExecutionContext 构造时预写入。
 */
@Slf4j
@Component
public class StartNodeExecutor implements NodeExecutor {

    @Override
    public String nodeType() {
        return "START";
    }

    @Override
    public void execute(WorkflowNode node, NodeConfig config, ExecutionContext ctx) throws Exception {
        StartNodeConfig start = (StartNodeConfig) config;
        log.info("[Workflow][{}] Start node '{}' executed, inputVariables={}",
                ctx.getWorkflowRunId(), node.getNodeKey(),
                start.inputVariables() == null ? 0 : start.inputVariables().size());
        // userMessage 已在 ExecutionContext 构造时写入 start.userMessage
    }
}

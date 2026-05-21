package com.hify.modules.workflow.domain.engine.executor;

import com.hify.modules.workflow.domain.engine.ExecutionContext;
import com.hify.modules.workflow.domain.nodeconfig.EndNodeConfig;
import com.hify.modules.workflow.domain.nodeconfig.NodeConfig;
import com.hify.modules.workflow.infra.entity.WorkflowNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 结束节点执行器。
 * <p>
 * END 节点无需额外处理，outputVariable 的值已从上游节点写入上下文。
 */
@Slf4j
@Component
public class EndNodeExecutor implements NodeExecutor {

    @Override
    public String nodeType() {
        return "END";
    }

    @Override
    public void execute(WorkflowNode node, NodeConfig config, ExecutionContext ctx) throws Exception {
        EndNodeConfig end = (EndNodeConfig) config;
        Object output = ctx.get(node.getNodeKey(), end.outputVariable());
        if (output == null) {
            // 兜底：从全局变量中查找 outputVariable 名称对应的值
            output = ctx.snapshot().get(end.outputVariable());
        }
        log.info("[Workflow][{}] End node '{}' reached, outputVariable={}",
                ctx.getWorkflowRunId(), node.getNodeKey(), output);
    }
}

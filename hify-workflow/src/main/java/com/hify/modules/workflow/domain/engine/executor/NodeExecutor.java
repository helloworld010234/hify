package com.hify.modules.workflow.domain.engine.executor;

import com.hify.modules.workflow.domain.engine.ExecutionContext;
import com.hify.modules.workflow.domain.nodeconfig.NodeConfig;
import com.hify.modules.workflow.infra.entity.WorkflowNode;

/**
 * 工作流节点执行器接口。
 */
public interface NodeExecutor {

    /**
     * 执行单个节点。
     *
     * @param node   节点实体（含 nodeKey、nodeType 等元信息）
     * @param config 已解析的节点配置（强类型 record）
     * @param ctx    执行上下文变量池
     */
    void execute(WorkflowNode node, NodeConfig config, ExecutionContext ctx) throws Exception;

    /**
     * 返回支持的节点类型 code。
     *
     * @return 如 "LLM"、"CONDITION" 等
     */
    String nodeType();
}

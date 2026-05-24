package com.hify.modules.workflow.service.impl.engine.executor;

import com.hify.modules.knowledge.api.KnowledgeRetrievalService;
import com.hify.modules.workflow.service.impl.engine.ExecutionContext;
import com.hify.modules.workflow.service.impl.nodeconfig.KnowledgeNodeConfig;
import com.hify.modules.workflow.service.impl.nodeconfig.NodeConfig;
import com.hify.modules.workflow.entity.WorkflowNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 知识库检索节点执行器。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KnowledgeNodeExecutor implements NodeExecutor {

    private final KnowledgeRetrievalService knowledgeRetrievalService;

    @Override
    public String nodeType() {
        return "KNOWLEDGE";
    }

    @Override
    public void execute(WorkflowNode node, NodeConfig config, ExecutionContext ctx) throws Exception {
        KnowledgeNodeConfig kb = (KnowledgeNodeConfig) config;
        String nodeKey = node.getNodeKey();

        // 1. 替换查询模板
        String resolvedQuery = ctx.resolve(kb.query());
        int topK = kb.topK() == null ? 5 : kb.topK();
        log.info("[Workflow][{}] Knowledge node '{}' query resolved: {}, topK={}",
                ctx.getWorkflowRunId(), nodeKey, resolvedQuery, topK);

        // 2. 调用知识库检索
        List<String> chunks = knowledgeRetrievalService.retrieve(
                kb.knowledgeBaseId(), resolvedQuery, topK, 0.7);

        log.info("[Workflow][{}] Knowledge node '{}' retrieved {} chunks",
                ctx.getWorkflowRunId(), nodeKey, chunks.size());

        // 3. 拼接结果并写入上下文
        String result = String.join("\n\n", chunks);
        ctx.set(nodeKey, kb.outputVariable(), result);
    }
}

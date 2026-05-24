package com.hify.modules.workflow.service.impl.engine.executor;

import com.hify.modules.provider.api.LlmService;
import com.hify.modules.provider.dto.chat.ChatMessage;
import com.hify.modules.provider.dto.chat.ChatRequest;
import com.hify.modules.provider.dto.chat.ChatResponse;
import com.hify.modules.workflow.service.impl.engine.ExecutionContext;
import com.hify.modules.workflow.service.impl.nodeconfig.LlmNodeConfig;
import com.hify.modules.workflow.service.impl.nodeconfig.NodeConfig;
import com.hify.modules.workflow.entity.WorkflowNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * LLM 调用节点执行器。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LlmNodeExecutor implements NodeExecutor {

    private final LlmService llmService;

    @Override
    public String nodeType() {
        return "LLM";
    }

    @Override
    public void execute(WorkflowNode node, NodeConfig config, ExecutionContext ctx) throws Exception {
        LlmNodeConfig llm = (LlmNodeConfig) config;
        String nodeKey = node.getNodeKey();

        // 1. 替换模板变量
        String resolvedPrompt = ctx.resolve(llm.prompt());
        log.info("[Workflow][{}] LLM node '{}' prompt resolved: {}", ctx.getWorkflowRunId(), nodeKey, resolvedPrompt);

        // 2. 构造请求
        ChatRequest request = new ChatRequest();
        request.setMessages(List.of(new ChatMessage("user", resolvedPrompt)));
        request.setStream(false);

        // 3. 同步调用 LLM
        ChatResponse response = llmService.chat(llm.modelConfigId(), request);
        String content = response.getContent();
        log.info("[Workflow][{}] LLM node '{}' response length: {}", ctx.getWorkflowRunId(), nodeKey, content == null ? 0 : content.length());

        // 4. 写入上下文
        ctx.set(nodeKey, llm.outputVariable(), content);
    }
}

package com.hify.modules.workflow.service.impl.engine.executor;

import com.hify.modules.workflow.service.impl.engine.ExecutionContext;
import com.hify.modules.workflow.service.impl.nodeconfig.ApiCallNodeConfig;
import com.hify.modules.workflow.service.impl.nodeconfig.NodeConfig;
import com.hify.modules.workflow.entity.WorkflowNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * HTTP 接口调用节点执行器。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ApiCallNodeExecutor implements NodeExecutor {

    private final RestTemplate restTemplate;

    @Override
    public String nodeType() {
        return "API_CALL";
    }

    @Override
    public void execute(WorkflowNode node, NodeConfig config, ExecutionContext ctx) throws Exception {
        ApiCallNodeConfig api = (ApiCallNodeConfig) config;
        String nodeKey = node.getNodeKey();

        // 1. 替换 URL 和 Body 中的模板变量
        String resolvedUrl = ctx.resolve(api.url());
        String resolvedBody = api.body() == null ? null : ctx.resolve(api.body());
        log.info("[Workflow][{}] API node '{}' URL resolved: {}", ctx.getWorkflowRunId(), nodeKey, resolvedUrl);

        // 2. 构造请求头（替换模板变量）
        HttpHeaders headers = new HttpHeaders();
        if (api.headers() != null) {
            api.headers().forEach((k, v) -> {
                headers.add(k, ctx.resolve(v));
            });
        }

        // 3. 发起 HTTP 请求
        HttpMethod method;
        try {
            method = HttpMethod.valueOf(api.method().toUpperCase());
        } catch (IllegalArgumentException e) {
            method = HttpMethod.GET;
        }
        HttpEntity<String> entity = new HttpEntity<>(resolvedBody, headers);
        ResponseEntity<String> response = restTemplate.exchange(resolvedUrl, method, entity, String.class);

        String responseBody = response.getBody();
        log.info("[Workflow][{}] API node '{}' status: {}, body length: {}",
                ctx.getWorkflowRunId(), nodeKey, response.getStatusCode().value(),
                responseBody == null ? 0 : responseBody.length());

        // 4. 写入上下文
        ctx.set(nodeKey, api.outputVariable(), responseBody);
    }
}

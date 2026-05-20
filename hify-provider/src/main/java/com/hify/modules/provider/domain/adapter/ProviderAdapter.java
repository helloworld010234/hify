package com.hify.modules.provider.domain.adapter;

import com.hify.modules.provider.api.dto.chat.ChatRequest;
import com.hify.modules.provider.api.dto.chat.ChatResponse;
import com.hify.modules.provider.api.dto.response.ConnectionTestResult;
import com.hify.modules.provider.infra.entity.Provider;

import java.io.IOException;
import java.util.List;
import java.util.function.Consumer;

/**
 * 供应商适配器接口（策略模式）
 * <p>
 * 每个 LLM 供应商实现此接口，封装该供应商特有的：
 * - 连通性测试（HTTP 端点、鉴权头、响应解析）
 * - 模型列表拉取
 * - 对话（同步 + 流式）
 */
public interface ProviderAdapter {

    /**
     * 执行连通性测试并返回结果（含延迟、模型数量、模型列表）
     */
    ConnectionTestResult testConnection(Provider provider);

    /**
     * 从远程拉取该供应商下的模型列表
     */
    List<ConnectionTestResult.ModelInfo> listModels(Provider provider) throws IOException;

    /**
     * 同步对话（非流式）
     */
    ChatResponse chat(Provider provider, ChatRequest request) throws IOException;

    /**
     * 流式对话（SSE）
     *
     * @param provider 供应商配置
     * @param request  请求（内部会被强制设置 stream=true）
     * @param onDelta  每收到一个 token 片段时回调
     * @param onFinish 流结束时回调（参数为 finishReason）
     * @throws IOException 网络或 HTTP 异常
     */
    void streamChat(Provider provider, ChatRequest request,
                    Consumer<String> onDelta, Consumer<String> onFinish) throws IOException;
}

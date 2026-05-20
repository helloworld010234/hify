package com.hify.modules.provider.api;

import com.hify.modules.provider.api.dto.chat.ChatRequest;
import com.hify.modules.provider.api.dto.chat.ChatResponse;

import java.io.IOException;
import java.util.function.Consumer;

/**
 * LLM 调用服务接口
 * <p>
 * 供其他模块调用，封装供应商适配器调度逻辑。
 */
public interface LlmService {

    /**
     * 同步对话（非流式）
     *
     * @param modelConfigId 模型配置 ID
     * @param request       请求
     * @return 响应
     * @throws IOException 网络或 HTTP 异常
     */
    ChatResponse chat(Long modelConfigId, ChatRequest request) throws IOException;

    /**
     * 流式对话（SSE）
     *
     * @param modelConfigId 模型配置 ID
     * @param request       请求（内部会被强制设置 stream=true）
     * @param onDelta       每收到一个 token 片段时回调
     * @param onFinish      流结束时回调（参数为 finishReason）
     * @throws IOException 网络或 HTTP 异常
     */
    void streamChat(Long modelConfigId, ChatRequest request,
                    Consumer<String> onDelta, Consumer<String> onFinish) throws IOException;
}

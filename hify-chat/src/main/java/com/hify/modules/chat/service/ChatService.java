package com.hify.modules.chat.service;

import com.hify.modules.chat.dto.request.ChatStreamRequest;
import com.hify.modules.chat.dto.response.ChatSessionResponse;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 对话服务接口
 */
public interface ChatService {

    /**
     * 创建新会话
     *
     * @param agentId Agent ID
     * @return 会话响应
     */
    ChatSessionResponse createSession(Long agentId);

    /**
     * 流式发送消息（SSE）
     *
     * @param sessionId 会话 ID
     * @param request   流式请求
     * @param emitter   SSE 发射器
     */
    void sendMessage(Long sessionId, ChatStreamRequest request, SseEmitter emitter);

    /**
     * 创建 SSE 流式发射器并异步执行对话。
     *
     * @param sessionId 会话 ID
     * @param message   用户消息
     * @return SSE 发射器
     */
    SseEmitter createStreamEmitter(Long sessionId, String message);
}

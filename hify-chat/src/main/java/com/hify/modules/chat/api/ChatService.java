package com.hify.modules.chat.api;

import com.hify.modules.chat.api.dto.request.ChatStreamRequest;
import com.hify.modules.chat.api.dto.response.ChatSessionResponse;
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
}

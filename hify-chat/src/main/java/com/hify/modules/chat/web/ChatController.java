package com.hify.modules.chat.web;

import com.hify.modules.chat.api.dto.request.ChatStreamRequest;
import com.hify.modules.chat.api.ChatService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.concurrent.ThreadPoolExecutor;

/**
 * 对话流式 Controller
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/chat/sessions")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;
    private final ThreadPoolExecutor llmStreamExecutor;

    @PostMapping(value = "/{sessionId}/messages", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter sendMessage(@PathVariable Long sessionId,
                                   @Valid @RequestBody ChatStreamRequest request) {
        SseEmitter emitter = new SseEmitter(300_000L); // 5 分钟超时
        llmStreamExecutor.execute(() -> {
            try {
                chatService.sendMessage(sessionId, request, emitter);
            } catch (Exception e) {
                log.error("LLM stream task failed", e);
                try {
                    emitter.completeWithError(e);
                } catch (Exception ex) {
                    log.warn("Failed to complete emitter with error", ex);
                }
            }
        });
        return emitter;
    }
}

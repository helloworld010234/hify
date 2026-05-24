package com.hify.modules.chat.controller;

import com.hify.modules.chat.dto.request.ChatStreamRequest;
import com.hify.modules.chat.service.ChatService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 对话流式 Controller
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/chat/sessions")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    @PostMapping(value = "/{sessionId}/messages", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter sendMessage(@PathVariable Long sessionId,
                                   @Valid @RequestBody ChatStreamRequest request) {
        return chatService.createStreamEmitter(sessionId, request.getMessage());
    }
}

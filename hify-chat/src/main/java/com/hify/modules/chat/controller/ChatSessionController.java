package com.hify.modules.chat.controller;

import com.hify.common.controller.Result;
import com.hify.modules.chat.dto.request.CreateSessionRequest;
import com.hify.modules.chat.service.ChatService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 对话会话 Controller
 */
@RestController
@RequestMapping("/api/v1/chat")
@RequiredArgsConstructor
public class ChatSessionController {

    private final ChatService chatService;

    @PostMapping("/sessions")
    public Result<com.hify.modules.chat.dto.response.ChatSessionResponse> create(@Valid @RequestBody CreateSessionRequest request) {
        return Result.ok(chatService.createSession(request.getAgentId()));
    }
}

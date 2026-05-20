package com.hify.modules.chat.web;

import com.hify.common.web.Result;
import com.hify.modules.chat.api.dto.request.CreateSessionRequest;
import com.hify.modules.chat.api.ChatService;

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
@RequestMapping("/api/v1/chat/sessions")
@RequiredArgsConstructor
public class ChatSessionController {

    private final ChatService chatService;

    @PostMapping
    public Result<com.hify.modules.chat.api.dto.response.ChatSessionResponse> create(@Valid @RequestBody CreateSessionRequest request) {
        return Result.ok(chatService.createSession(request.getAgentId()));
    }
}

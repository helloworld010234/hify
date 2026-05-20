package com.hify.modules.provider.api.dto.chat;

import lombok.Data;

/**
 * LLM 聊天响应 DTO（同步非流式）
 */
@Data
public class ChatResponse {

    private String id;
    private String model;
    private String finishReason;
    private String content;
    private Usage usage;
}

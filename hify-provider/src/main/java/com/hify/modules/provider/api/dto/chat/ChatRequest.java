package com.hify.modules.provider.api.dto.chat;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * LLM 聊天请求 DTO
 */
@Data
public class ChatRequest {

    private String model;
    private List<ChatMessage> messages;
    private Boolean stream = false;
    private BigDecimal temperature;
    private Integer maxTokens;
}

package com.hify.modules.provider.dto.chat;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

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

    /**
     * 工具定义列表（OpenAI tools 格式）
     */
    private List<Map<String, Object>> tools;
}

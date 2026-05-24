package com.hify.modules.provider.dto.chat;

import lombok.Data;

import java.util.List;
import java.util.Map;

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

    /**
     * 工具调用列表（finish_reason=tool_calls 时返回）
     */
    private List<Map<String, Object>> toolCalls;
}

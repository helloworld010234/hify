package com.hify.modules.provider.dto.chat;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * LLM 对话消息 DTO（OpenAI 格式）
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessage {

    private String role;
    private String content;

    /**
     * 工具调用声明（role=assistant 时，当 LLM 决定调用工具）
     */
    private List<Map<String, Object>> toolCalls;

    /**
     * 工具调用 ID（role=tool 时必须携带，与 assistant 消息中的 tool_calls.id 对应）
     */
    private String toolCallId;

    public ChatMessage(String role, String content) {
        this.role = role;
        this.content = content;
    }
}

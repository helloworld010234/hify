package com.hify.modules.chat.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * SSE 流式事件 DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatStreamEvent {

    private String type;
    private String content;
    private String finishReason;
    private Long latencyMs;
    private String code;
    private String message;

    public static ChatStreamEvent delta(String content) {
        return new ChatStreamEvent("delta", content, null, null, null, null);
    }

    public static ChatStreamEvent done(String finishReason, long latencyMs) {
        return new ChatStreamEvent("done", null, finishReason, latencyMs, null, null);
    }

    public static ChatStreamEvent error(String code, String message) {
        return new ChatStreamEvent("error", null, null, null, code, message);
    }
}

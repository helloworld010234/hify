package com.hify.modules.chat.service.assembler;

import com.hify.common.util.TokenUtil;
import com.hify.modules.chat.entity.ChatMessage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ChatContextAssemblerTest {

    private final ChatContextAssembler assembler = new ChatContextAssembler();

    @Test
    @DisplayName("P0-3: FIXED_TURNS 策略 — 只保留最近 N 轮")
    void assemble_fixedTurns_shouldLimitMessages() {
        List<ChatMessage> history = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            ChatMessage m = new ChatMessage();
            m.setRole(i % 2 == 0 ? "user" : "assistant");
            m.setContent("msg" + i);
            history.add(m);
        }

        var result = assembler.assemble(history, "system", "current", "FIXED_TURNS", 4096, 2);

        assertEquals(6, result.size()); // system + 2轮(4条) + current
        assertEquals("system", result.get(0).getRole());
        assertEquals("user", result.get(1).getRole());
        assertEquals("msg6", result.get(1).getContent());
        assertEquals("user", result.get(5).getRole());
        assertEquals("current", result.get(5).getContent());
    }

    @Test
    @DisplayName("P0-3: SLIDING_WINDOW 策略 — token 预算内保留最近消息")
    void assemble_slidingWindow_shouldRespectBudget() {
        List<ChatMessage> history = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            ChatMessage m = new ChatMessage();
            m.setRole(i % 2 == 0 ? "user" : "assistant");
            m.setContent("一二三四五六七八九十"); // 10 个中文字
            m.setTokens(15);
            history.add(m);
        }

        var result = assembler.assemble(history, null, "current", "SLIDING_WINDOW", 4096, 10);
        assertEquals(11, result.size()); // 10 历史 + current
    }

    @Test
    @DisplayName("P0-3: SLIDING_WINDOW 策略 — 超出预算时截断旧消息")
    void assemble_slidingWindow_shouldTruncateOldMessages() {
        List<ChatMessage> history = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            ChatMessage m = new ChatMessage();
            m.setRole(i % 2 == 0 ? "user" : "assistant");
            m.setContent("一二三四五六七八九十"); // 10 中文字 ≈ 15 tokens
            m.setTokens(15);
            history.add(m);
        }

        var result = assembler.assemble(history, "system", "current", "SLIDING_WINDOW", 100, 10);

        assertTrue(result.size() >= 4 && result.size() <= 7,
                "Expected 4~7 messages but got " + result.size());
        assertEquals("system", result.get(0).getRole());
        assertEquals("user", result.get(result.size() - 1).getRole());
        assertEquals("current", result.get(result.size() - 1).getContent());
    }

    @Test
    @DisplayName("P0-3: token 估算 — 中文和英文混合")
    void estimateTokens_mixedContent() {
        int tokens = TokenUtil.estimateTokens("一二三四五六七八九十abcdefghij");
        assertEquals(18, tokens);
    }
}

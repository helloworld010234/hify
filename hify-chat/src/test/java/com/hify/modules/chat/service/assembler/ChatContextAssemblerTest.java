package com.hify.modules.chat.service.assembler;


import com.hify.modules.chat.entity.ChatMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class ChatContextAssemblerTest {

    private final ChatContextAssembler assembler = new ChatContextAssembler();

    @Test
    void should_selectFixedTurns_when_strategyIsFixedTurns() {
        // Given
        List<ChatMessage> history = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            ChatMessage msg = new ChatMessage();
            msg.setRole(i % 2 == 0 ? "user" : "assistant");
            msg.setContent("msg-" + i);
            msg.setTokens(10);
            history.add(msg);
        }

        // When
        List<com.hify.modules.provider.dto.chat.ChatMessage> result = assembler.assemble(
                history, "system-prompt", "current-msg", "FIXED_TURNS", 4096, 5);

        // Then
        assertThat(result).hasSize(12); // system + 5*2 history + user
        assertThat(result.get(0).getRole()).isEqualTo("system");
        assertThat(result.get(result.size() - 1).getRole()).isEqualTo("user");
        assertThat(result.get(result.size() - 1).getContent()).isEqualTo("current-msg");
        // Should keep last 10 messages (5 turns * 2)
        assertThat(result.get(1).getContent()).isEqualTo("msg-20");
        assertThat(result.get(10).getContent()).isEqualTo("msg-29");
    }

    @Test
    void should_truncateByTokenBudget_when_slidingWindowExceeds() {
        // Given
        List<ChatMessage> history = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            ChatMessage msg = new ChatMessage();
            msg.setRole(i % 2 == 0 ? "user" : "assistant");
            msg.setContent("msg-" + i);
            msg.setTokens(500); // each message 500 tokens
            history.add(msg);
        }

        // maxContextTokens = 2000, budget = 2000 * 0.7 = 1400
        // So only ~2 messages (1000 tokens) should fit, plus current user message

        // When
        List<com.hify.modules.provider.dto.chat.ChatMessage> result = assembler.assemble(
                history, "system-prompt", "current-msg", "SLIDING_WINDOW", 2000, 10);

        // Then
        assertThat(result.get(0).getRole()).isEqualTo("system");
        assertThat(result.get(result.size() - 1).getRole()).isEqualTo("user");
        // Should include system + last 2 history + current user
        assertThat(result).hasSize(4);
        assertThat(result.get(1).getContent()).isEqualTo("msg-8");
        assertThat(result.get(2).getContent()).isEqualTo("msg-9");
    }

    @Test
    void should_returnOriginalPrompt_when_kbNotBound() {
        // Given
        List<ChatMessage> history = new ArrayList<>();

        // When
        List<com.hify.modules.provider.dto.chat.ChatMessage> result = assembler.assemble(
                history, null, "current-msg", "SLIDING_WINDOW", 4096, 10);

        // Then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getRole()).isEqualTo("user");
        assertThat(result.get(0).getContent()).isEqualTo("current-msg");
    }
}

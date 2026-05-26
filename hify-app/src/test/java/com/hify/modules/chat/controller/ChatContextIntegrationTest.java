package com.hify.modules.chat.controller;

import com.hify.AbstractIntegrationTest;
import com.hify.modules.provider.api.LlmService;
import com.hify.modules.provider.dto.chat.ChatRequest;
import com.hify.modules.provider.dto.chat.ChatMessage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 对话上下文多轮正确性集成测试（场景三）
 * <p>
 * 对应已知 bug：selectRecentBySessionId 的 SQL 使用 ORDER BY created_at ASC LIMIT，
 * 取的是最旧的消息而非最新的，导致 LLM 丢失最近的多轮上下文。
 * <p>
 * 注意：SSE 流在独立线程池中执行，禁用测试事务。
 */
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ChatContextIntegrationTest extends AbstractIntegrationTest {

    @MockBean
    private LlmService llmService;

    @AfterEach
    void cleanupChatTestData() {
        jdbcTemplate.update("DELETE FROM t_chat_message WHERE session_id = 500");
        jdbcTemplate.update("DELETE FROM t_chat_session WHERE id = 500");
        jdbcTemplate.update("DELETE FROM t_agent WHERE id = 500");
        jdbcTemplate.update("DELETE FROM t_model WHERE id = 500");
        jdbcTemplate.update("DELETE FROM t_provider WHERE id = 500");
    }

    @Test
    @DisplayName("P0: 多轮上下文传递 — 第3条消息应包含'第一条'和'第二条'且顺序正确")
    @Sql(scripts = "classpath:db/chat-context-test-data.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
    void multiRoundContext_shouldIncludeHistoryInCorrectOrder() throws Exception {
        // Given: Mock LLM 每次返回固定回答
        doAnswer(invocation -> {
            Consumer<String> onDelta = invocation.getArgument(2);
            Consumer<String> onFinish = invocation.getArgument(3);
            onDelta.accept("回答");
            onFinish.accept("stop");
            return null;
        }).when(llmService).streamChat(anyLong(), any(ChatRequest.class), any(), any());

        // When: 同一 session 依次发送 3 条消息
        sendMessageAndWait(500L, "第一条");
        sendMessageAndWait(500L, "第二条");
        sendMessageAndWait(500L, "第三条");

        // Then: 拦截第 3 次 streamChat 收到的 ChatRequest
        ArgumentCaptor<ChatRequest> requestCaptor = ArgumentCaptor.forClass(ChatRequest.class);
        verify(llmService, times(3)).streamChat(anyLong(), requestCaptor.capture(), any(), any());

        ChatRequest thirdRequest = requestCaptor.getAllValues().get(2);
        List<ChatMessage> messages = thirdRequest.getMessages();

        // ① 包含"第一条"和"第二条"的历史消息
        List<String> contents = messages.stream()
                .map(ChatMessage::getContent)
                .toList();
        assertThat(contents).contains("第一条", "第二条");

        // ② 历史消息按时间升序排列（先旧后新）
        int idxFirst = indexOfContent(messages, "第一条");
        int idxSecond = indexOfContent(messages, "第二条");
        assertThat(idxFirst).isGreaterThanOrEqualTo(0);
        assertThat(idxSecond).isGreaterThanOrEqualTo(0);
        assertThat(idxFirst).isLessThan(idxSecond);

        // ③ 最后一条是"第三条"（当前用户输入）
        ChatMessage lastMessage = messages.get(messages.size() - 1);
        assertThat(lastMessage.getRole()).isEqualTo("user");
        assertThat(lastMessage.getContent()).isEqualTo("第三条");
    }

    private void sendMessageAndWait(Long sessionId, String message) throws Exception {
        MvcResult asyncResult = mockMvc.perform(post("/api/v1/chat/sessions/" + sessionId + "/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"" + message + "\"}"))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(asyncResult))
                .andExpect(status().isOk())
                .andReturn();
    }

    private int indexOfContent(List<ChatMessage> messages, String content) {
        for (int i = 0; i < messages.size(); i++) {
            if (content.equals(messages.get(i).getContent())) {
                return i;
            }
        }
        return -1;
    }
}

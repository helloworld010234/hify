package com.hify.modules.chat.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.hify.AbstractIntegrationTest;
import com.hify.modules.provider.api.LlmService;
import com.hify.modules.provider.dto.chat.ChatRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mockingDetails;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Chat 完整对话链路集成测试（场景一：普通问答）
 * <p>
 * 注意：SSE 流在独立线程池中执行，该线程无法继承主线程的 Spring 事务上下文。
 * 因此本类禁用测试事务，由 {@code @Sql} 在独立事务中预置数据，测试结束后手动清理。
 */
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ChatCommonIntegrationTest extends AbstractIntegrationTest {

    @MockBean
    private LlmService llmService;

    @AfterEach
    void cleanupChatTestData() {
        jdbcTemplate.update("DELETE FROM t_chat_message WHERE session_id = 300");
        jdbcTemplate.update("DELETE FROM t_chat_session WHERE id = 300");
        jdbcTemplate.update("DELETE FROM t_agent WHERE id = 300");
        jdbcTemplate.update("DELETE FROM t_model WHERE id = 300");
        jdbcTemplate.update("DELETE FROM t_provider WHERE id = 300");
    }

    @Test
    @DisplayName("P0: 完整对话链路 — 普通问答，SSE 流式返回 + 消息落库")
    @Sql(scripts = "classpath:db/chat-common-test-data.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
    void chatStream_normalQuestion_shouldReturnDeltaAndPersistMessages() throws Exception {
        // Given: Mock LLM 返回 "你好！"
        doAnswer(invocation -> {
            Consumer<String> onDelta = invocation.getArgument(2);
            Consumer<String> onFinish = invocation.getArgument(3);
            onDelta.accept("你");
            onDelta.accept("好");
            onDelta.accept("！");
            onFinish.accept("stop");
            return null;
        }).when(llmService).streamChat(anyLong(), any(ChatRequest.class), any(), any());

        // When: 发起 SSE 请求（异步）
        MvcResult asyncResult = mockMvc.perform(post("/api/v1/chat/sessions/300/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"你好\"}"))
                .andExpect(request().asyncStarted())
                .andReturn();

        // 派发异步结果，等待 SSE 流完成
        MvcResult dispatchedResult = mockMvc.perform(asyncDispatch(asyncResult))
                .andExpect(status().isOk())
                .andReturn();

        // MockHttpServletResponse 的 getContentAsString() 可能因编码问题导致中文乱码，
        // 显式使用 UTF-8 解码字节数组
        String responseBody = new String(
                dispatchedResult.getResponse().getContentAsByteArray(),
                StandardCharsets.UTF_8);

        // 解析 SSE 事件
        List<SseEvent> events = parseSseEvents(responseBody);

        // Then ①: SSE 流包含至少一个 type=delta 的事件，content 非空
        List<SseEvent> deltaEvents = events.stream()
                .filter(e -> "delta".equals(e.type))
                .toList();
        assertThat(deltaEvents).isNotEmpty();
        assertThat(deltaEvents).allMatch(e -> e.content != null && !e.content.isEmpty());

        // Then ②: 最后一个事件是 type=done
        assertThat(events).isNotEmpty();
        SseEvent lastEvent = events.get(events.size() - 1);
        assertThat(lastEvent.type).isEqualTo("done");
        assertThat(lastEvent.finishReason).isEqualTo("stop");

        // Then ③: 数据库有 user + assistant 各一条
        List<Map<String, Object>> messages = jdbcTemplate.queryForList(
                "SELECT * FROM t_chat_message WHERE session_id = 300 AND deleted = 0 ORDER BY id");
        assertThat(messages).hasSize(2);

        Map<String, Object> userMsg = messages.get(0);
        assertThat(userMsg.get("role")).isEqualTo("user");
        assertThat(userMsg.get("content")).isEqualTo("你好");

        Map<String, Object> assistantMsg = messages.get(1);
        assertThat(assistantMsg.get("role")).isEqualTo("assistant");

        // Then ④: assistant content 与所有 delta 拼接一致
        String deltaContent = deltaEvents.stream()
                .map(e -> e.content)
                .reduce("", String::concat);
        assertThat(assistantMsg.get("content")).isEqualTo(deltaContent);
    }

    /**
     * 解析 SSE 响应体为事件列表
     */
    private List<SseEvent> parseSseEvents(String body) {
        List<SseEvent> events = new ArrayList<>();
        for (String line : body.split("\n")) {
            line = line.trim();
            if (line.startsWith("data:")) {
                String json = line.substring(5).trim();
                if (!json.isEmpty() && !"[DONE]".equals(json)) {
                    try {
                        JsonNode node = objectMapper.readTree(json);
                        events.add(new SseEvent(
                                node.path("type").asText(null),
                                node.path("content").asText(null),
                                node.path("finishReason").asText(null),
                                node.path("latencyMs").asLong(0),
                                node.path("code").asText(null),
                                node.path("message").asText(null)
                        ));
                    } catch (Exception e) {
                        // 忽略解析失败的行
                    }
                }
            }
        }
        return events;
    }

    private record SseEvent(String type, String content, String finishReason,
                            long latencyMs, String code, String message) {}
}

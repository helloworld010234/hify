package com.hify.modules.chat.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.hify.AbstractIntegrationTest;
import com.hify.modules.knowledge.api.KnowledgeRetrievalService;
import com.hify.modules.provider.api.LlmService;
import com.hify.modules.provider.dto.chat.ChatRequest;
import com.hify.modules.workflow.api.WorkflowRunService;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Chat 增强集成测试 — SSE 流式对话 + RAG 触发 + 工作流触发
 * <p>
 * 注意：SSE 流在独立线程池中执行，该线程无法继承主线程的 Spring 事务上下文。
 * 因此本类禁用测试事务，由 {@code @Sql} 在独立事务中预置数据，测试结束后手动清理。
 */
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ChatControllerEnhancedIT extends AbstractIntegrationTest {

    @MockBean
    private LlmService llmService;

    @MockBean
    private KnowledgeRetrievalService knowledgeRetrievalService;

    @MockBean
    private WorkflowRunService workflowRunService;

    @AfterEach
    void cleanupEnhancedChatTestData() {
        jdbcTemplate.update("DELETE FROM t_chat_message WHERE session_id IN (700, 701, 702, 730)");
        jdbcTemplate.update("DELETE FROM t_chat_session WHERE id IN (700, 701, 702, 730)");
        jdbcTemplate.update("DELETE FROM t_agent WHERE id IN (700, 701, 702)");
        jdbcTemplate.update("DELETE FROM t_model WHERE id IN (700, 720, 721)");
        jdbcTemplate.update("DELETE FROM t_provider WHERE id IN (700, 710, 720, 721, 730)");
    }

    @Test
    @DisplayName("P0: SSE 流式对话 — 无工具绑定，消息正确落库")
    @Sql(scripts = "classpath:db/chat-enhanced-test-data.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
    void should_streamAssistantMessage_andPersist_when_noToolBound() throws Exception {
        doAnswer(invocation -> {
            Consumer<String> onDelta = invocation.getArgument(2);
            Consumer<String> onFinish = invocation.getArgument(3);
            onDelta.accept("Hello");
            onDelta.accept(" world");
            onFinish.accept("stop");
            return null;
        }).when(llmService).streamChat(anyLong(), any(ChatRequest.class), any(), any());

        MvcResult asyncResult = mockMvc.perform(post("/api/v1/chat/sessions/700/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"Hi\"}"))
                .andExpect(request().asyncStarted())
                .andReturn();

        MvcResult dispatched = mockMvc.perform(asyncDispatch(asyncResult))
                .andExpect(status().isOk())
                .andReturn();

        String body = new String(dispatched.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
        List<SseEvent> events = parseSseEvents(body);

        assertThat(events).isNotEmpty();
        assertThat(events.get(events.size() - 1).type).isEqualTo("done");

        List<Map<String, Object>> messages = jdbcTemplate.queryForList(
                "SELECT * FROM t_chat_message WHERE session_id = 700 AND deleted = 0 ORDER BY id");
        assertThat(messages).hasSize(2);
        assertThat(messages.get(0).get("role")).isEqualTo("user");
        assertThat(messages.get(1).get("role")).isEqualTo("assistant");
    }

    @Test
    @DisplayName("P0: Agent 绑定知识库 — 对话触发 RAG 检索")
    @Sql(scripts = "classpath:db/chat-enhanced-test-data.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
    void should_triggerRag_when_kbBound() throws Exception {
        when(knowledgeRetrievalService.retrieve(eq(500L), any(), eq(3), eq(0.35)))
                .thenReturn(List.of("Retrieved chunk 1", "Retrieved chunk 2"));

        doAnswer(invocation -> {
            Consumer<String> onDelta = invocation.getArgument(2);
            Consumer<String> onFinish = invocation.getArgument(3);
            onDelta.accept("Answer");
            onFinish.accept("stop");
            return null;
        }).when(llmService).streamChat(anyLong(), any(ChatRequest.class), any(), any());

        MvcResult asyncResult = mockMvc.perform(post("/api/v1/chat/sessions/701/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"What is AI?\"}"))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(asyncResult)).andExpect(status().isOk());

        verify(knowledgeRetrievalService).retrieve(eq(500L), any(), eq(3), eq(0.35));
    }

    @Test
    @DisplayName("P0: Agent 绑定工作流 — 对话触发工作流执行")
    @Sql(scripts = "classpath:db/chat-enhanced-test-data.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
    void should_triggerWorkflow_when_workflowBound() throws Exception {
        when(workflowRunService.run(eq(100L), any()))
                .thenReturn("Workflow output");

        MvcResult asyncResult = mockMvc.perform(post("/api/v1/chat/sessions/702/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"Run workflow\"}"))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(asyncResult)).andExpect(status().isOk());

        verify(workflowRunService).run(eq(100L), any());
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
                                node.path("finishReason").asText(null)
                        ));
                    } catch (Exception e) {
                        // ignore
                    }
                }
            }
        }
        return events;
    }

    private record SseEvent(String type, String content, String finishReason) {}
}

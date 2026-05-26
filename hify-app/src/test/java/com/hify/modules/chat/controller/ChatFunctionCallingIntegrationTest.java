package com.hify.modules.chat.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hify.AbstractIntegrationTest;
import com.hify.common.service.mcp.McpToolDefinition;
import com.hify.common.service.mcp.McpToolService;
import com.hify.modules.mcp.api.McpClientService;
import com.hify.modules.provider.api.LlmService;
import com.hify.modules.provider.dto.chat.ChatRequest;
import com.hify.modules.provider.dto.chat.ChatResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Chat Function Calling 两轮链路集成测试（场景二）
 * <p>
 * 注意：SSE 流在独立线程池中执行，该线程无法继承主线程的 Spring 事务上下文。
 * 因此本类禁用测试事务，由 {@code @Sql} 在独立事务中预置数据，测试结束后手动清理。
 */
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ChatFunctionCallingIntegrationTest extends AbstractIntegrationTest {

    @MockBean
    private LlmService llmService;

    @MockBean
    private McpToolService mcpToolService;

    @MockBean
    private McpClientService mcpClientService;

    @Autowired
    private ObjectMapper objectMapper;

    @AfterEach
    void cleanupChatTestData() {
        jdbcTemplate.update("DELETE FROM t_chat_message WHERE session_id = 400");
        jdbcTemplate.update("DELETE FROM t_chat_session WHERE id = 400");
        jdbcTemplate.update("DELETE FROM t_agent_tool WHERE agent_id = 400");
        jdbcTemplate.update("DELETE FROM t_agent WHERE id = 400");
        jdbcTemplate.update("DELETE FROM t_mcp_tool WHERE id = 400");
        jdbcTemplate.update("DELETE FROM t_mcp_server WHERE id = 400");
        jdbcTemplate.update("DELETE FROM t_model WHERE id = 400");
        jdbcTemplate.update("DELETE FROM t_provider WHERE id = 400");
    }

    @Test
    @DisplayName("P0: Function Calling 两轮链路 — 工具调用成功，SSE 正常结束 + 单条 assistant 落库")
    @Sql(scripts = "classpath:db/chat-function-calling-test-data.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
    void chatStream_toolCallSuccess_shouldExecuteToolAndReturnFinalAnswer() throws Exception {
        // Given ①: Mock 工具 schema
        McpToolDefinition toolDef = new McpToolDefinition();
        toolDef.setId(400L);
        toolDef.setServerId(400L);
        toolDef.setName("check_refund_eligibility");
        toolDef.setDescription("Check if an order is eligible for refund");
        JsonNode inputSchema = objectMapper.readTree(
                "{\"type\":\"object\",\"properties\":{\"order_id\":{\"type\":\"string\",\"description\":\"Order ID\"}},\"required\":[\"order_id\"]}");
        toolDef.setInputSchema(inputSchema);
        when(mcpToolService.getToolDefinitions(List.of(400L))).thenReturn(List.of(toolDef));

        // Given ②: Mock MCP 工具执行结果
        when(mcpClientService.callTool(eq(400L), eq("check_refund_eligibility"), any()))
                .thenReturn("符合退款条件");

        // Given ③: Mock 第一轮 LLM 同步调用返回 tool_calls
        ChatResponse firstResponse = new ChatResponse();
        firstResponse.setFinishReason("tool_calls");
        firstResponse.setContent(null);
        firstResponse.setToolCalls(List.of(
                Map.of(
                        "id", "call_123",
                        "function", Map.of(
                                "name", "check_refund_eligibility",
                                "arguments", "{\"order_id\":\"12345\"}"
                        )
                )
        ));
        when(llmService.chat(anyLong(), any(ChatRequest.class))).thenReturn(firstResponse);

        // Given ④: Mock 第二轮 LLM 流式调用返回最终回答
        doAnswer(invocation -> {
            Consumer<String> onDelta = invocation.getArgument(2);
            Consumer<String> onFinish = invocation.getArgument(3);
            onDelta.accept("您");
            onDelta.accept("符合");
            onDelta.accept("退款");
            onDelta.accept("条件");
            onFinish.accept("stop");
            return null;
        }).when(llmService).streamChat(anyLong(), any(ChatRequest.class), any(), any());

        // When: 发起 SSE 请求
        MvcResult asyncResult = mockMvc.perform(post("/api/v1/chat/sessions/400/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"帮我查一下订单 12345 能不能退款\"}"))
                .andExpect(request().asyncStarted())
                .andReturn();

        MvcResult dispatchedResult = mockMvc.perform(asyncDispatch(asyncResult))
                .andExpect(status().isOk())
                .andReturn();

        String responseBody = new String(
                dispatchedResult.getResponse().getContentAsByteArray(),
                StandardCharsets.UTF_8);

        List<Map<String, Object>> events = parseSseEvents(responseBody);

        // Then ①: SSE 流正常结束，最后事件是 type=done
        assertThat(events).isNotEmpty();
        Map<String, Object> lastEvent = events.get(events.size() - 1);
        assertThat(lastEvent.get("type")).isEqualTo("done");
        assertThat(lastEvent.get("finishReason")).isEqualTo("stop");

        // Then ②: 数据库只有一条 assistant 消息（两轮 LLM 只落一次）
        List<Map<String, Object>> messages = jdbcTemplate.queryForList(
                "SELECT * FROM t_chat_message WHERE session_id = 400 AND deleted = 0 ORDER BY id");
        assertThat(messages).hasSize(2);

        Map<String, Object> userMsg = messages.get(0);
        assertThat(userMsg.get("role")).isEqualTo("user");

        Map<String, Object> assistantMsg = messages.get(1);
        assertThat(assistantMsg.get("role")).isEqualTo("assistant");

        // Then ③: 验证 MCP 工具被调用
        verify(mcpClientService).callTool(any(), eq("check_refund_eligibility"), any());
    }

    @Test
    @DisplayName("P0: Function Calling 失败容错 — MCP 调用抛异常，SSE 不挂起正常结束")
    @Sql(scripts = "classpath:db/chat-function-calling-test-data.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
    void chatStream_toolCallFailed_shouldNotHangAndReturnDone() throws Exception {
        // Given ①: Mock 工具 schema
        McpToolDefinition toolDef = new McpToolDefinition();
        toolDef.setId(400L);
        toolDef.setServerId(400L);
        toolDef.setName("check_refund_eligibility");
        toolDef.setDescription("Check if an order is eligible for refund");
        JsonNode inputSchema = objectMapper.readTree(
                "{\"type\":\"object\",\"properties\":{\"order_id\":{\"type\":\"string\",\"description\":\"Order ID\"}},\"required\":[\"order_id\"]}");
        toolDef.setInputSchema(inputSchema);
        when(mcpToolService.getToolDefinitions(List.of(400L))).thenReturn(List.of(toolDef));

        // Given ②: Mock MCP 工具执行抛 RuntimeException（review 发现的高危场景）
        doThrow(new RuntimeException("MCP 服务不可用"))
                .when(mcpClientService).callTool(eq(400L), eq("check_refund_eligibility"), any());

        // Given ③: Mock 第一轮 LLM 同步调用返回 tool_calls
        ChatResponse firstResponse = new ChatResponse();
        firstResponse.setFinishReason("tool_calls");
        firstResponse.setContent(null);
        firstResponse.setToolCalls(List.of(
                Map.of(
                        "id", "call_123",
                        "function", Map.of(
                                "name", "check_refund_eligibility",
                                "arguments", "{\"order_id\":\"12345\"}"
                        )
                )
        ));
        when(llmService.chat(anyLong(), any(ChatRequest.class))).thenReturn(firstResponse);

        // Given ④: Mock 第二轮 LLM 流式调用返回最终回答（带错误信息）
        doAnswer(invocation -> {
            Consumer<String> onDelta = invocation.getArgument(2);
            Consumer<String> onFinish = invocation.getArgument(3);
            onDelta.accept("抱歉");
            onDelta.accept("，");
            onDelta.accept("工具调用失败");
            onFinish.accept("stop");
            return null;
        }).when(llmService).streamChat(anyLong(), any(ChatRequest.class), any(), any());

        // When: 发起 SSE 请求
        MvcResult asyncResult = mockMvc.perform(post("/api/v1/chat/sessions/400/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"帮我查一下订单 12345 能不能退款\"}"))
                .andExpect(request().asyncStarted())
                .andReturn();

        MvcResult dispatchedResult = mockMvc.perform(asyncDispatch(asyncResult))
                .andExpect(status().isOk())
                .andReturn();

        String responseBody = new String(
                dispatchedResult.getResponse().getContentAsByteArray(),
                StandardCharsets.UTF_8);

        List<Map<String, Object>> events = parseSseEvents(responseBody);

        // Then ①: SSE 流正常结束，最后事件是 type=done（不挂起）
        assertThat(events).isNotEmpty();
        Map<String, Object> lastEvent = events.get(events.size() - 1);
        assertThat(lastEvent.get("type")).isEqualTo("done");
        assertThat(lastEvent.get("finishReason")).isEqualTo("stop");

        // Then ②: 数据库只有一条 assistant 消息
        List<Map<String, Object>> messages = jdbcTemplate.queryForList(
                "SELECT * FROM t_chat_message WHERE session_id = 400 AND deleted = 0 ORDER BY id");
        assertThat(messages).hasSize(2);

        Map<String, Object> assistantMsg = messages.get(1);
        assertThat(assistantMsg.get("role")).isEqualTo("assistant");

        // Then ③: 验证 MCP 工具确实被调用了（即使抛异常）
        verify(mcpClientService).callTool(any(), eq("check_refund_eligibility"), any());
    }

    /**
     * 解析 SSE 响应体为事件列表（简化版）
     */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> parseSseEvents(String body) throws Exception {
        List<Map<String, Object>> events = new java.util.ArrayList<>();
        for (String line : body.split("\n")) {
            line = line.trim();
            if (line.startsWith("data:")) {
                String json = line.substring(5).trim();
                if (!json.isEmpty() && !"[DONE]".equals(json)) {
                    events.add(objectMapper.readValue(json, Map.class));
                }
            }
        }
        return events;
    }
}

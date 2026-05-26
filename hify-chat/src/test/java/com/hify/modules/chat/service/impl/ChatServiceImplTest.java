package com.hify.modules.chat.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hify.common.exception.BizException;
import com.hify.common.exception.ErrorCode;
import com.hify.common.service.mcp.McpToolDefinition;
import com.hify.common.service.mcp.McpToolService;

import com.hify.modules.agent.api.AgentService;
import com.hify.modules.agent.dto.response.AgentDetailResponse;
import com.hify.modules.chat.dto.request.ChatStreamRequest;
import com.hify.modules.chat.dto.response.ChatStreamEvent;
import com.hify.modules.chat.entity.ChatMessage;
import com.hify.modules.chat.entity.ChatSession;
import com.hify.modules.chat.mapper.ChatMessageMapper;
import com.hify.modules.chat.mapper.ChatSessionMapper;
import com.hify.modules.chat.service.assembler.ChatContextAssembler;
import com.hify.modules.knowledge.api.KnowledgeRetrievalService;
import com.hify.modules.mcp.api.McpClientService;
import com.hify.modules.provider.api.LlmService;
import com.hify.modules.provider.dto.chat.ChatRequest;
import com.hify.modules.provider.dto.chat.ChatResponse;
import com.hify.modules.workflow.api.WorkflowRunService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatServiceImplTest {

    @Mock
    private AgentService agentService;

    @Mock
    private ChatPersistenceService persistenceService;

    @Mock
    private ChatContextAssembler contextAssembler;

    @Mock
    private LlmService llmService;

    @Mock
    private ChatSessionMapper chatSessionMapper;

    @Mock
    private ChatMessageMapper chatMessageMapper;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private KnowledgeRetrievalService knowledgeRetrievalService;

    @Mock
    private McpToolService mcpToolService;

    @Mock
    private McpClientService mcpClientService;

    @Mock
    private WorkflowRunService workflowRunService;

    @Mock
    private ThreadPoolExecutor llmStreamExecutor;

    @InjectMocks
    private ChatServiceImpl chatService;

    private ChatSession session;
    private AgentDetailResponse agent;
    private ChatStreamRequest request;
    private SseEmitter emitter;

    @BeforeEach
    void setUp() {
        session = new ChatSession();
        session.setId(1L);
        session.setAgentId(2L);
        session.setTitle("新会话");
        session.setDeleted(0);

        agent = new AgentDetailResponse();
        agent.setId(2L);
        agent.setName("TestAgent");
        agent.setSystemPrompt("You are a helpful assistant.");
        agent.setModelConfigId(10L);
        agent.setTemperature(new BigDecimal("0.7"));
        agent.setMaxTokens(2048);
        agent.setMaxContextTurns(10);
        agent.setEnabled(1);

        request = new ChatStreamRequest();
        request.setMessage("Hello");
        request.setContextStrategy("SLIDING_WINDOW");

        emitter = new SseEmitter(300_000L);
    }

    @Test
    void should_saveUserMessageAndStream_when_noToolBound() throws IOException {
        // Given
        when(chatSessionMapper.selectById(1L)).thenReturn(session);
        when(agentService.getById(2L)).thenReturn(agent);
        when(chatMessageMapper.selectRecentBySessionId(anyLong(), anyInt())).thenReturn(Collections.emptyList());
        when(contextAssembler.assemble(any(), anyString(), anyString(), anyString(), any(), any())).thenReturn(new ArrayList<>());

        doAnswer(invocation -> {
            Consumer<String> onDelta = invocation.getArgument(2);
            Consumer<String> onFinish = invocation.getArgument(3);
            onDelta.accept("Hi");
            onFinish.accept("stop");
            return null;
        }).when(llmService).streamChat(anyLong(), any(ChatRequest.class), any(), any());

        // When
        chatService.sendMessage(1L, request, emitter);

        // Then
        verify(persistenceService).saveUserMessage(1L, "Hello");
        verify(llmService).streamChat(eq(10L), any(ChatRequest.class), any(), any());
    }

    @Test
    void should_executeToolCallsAndStreamFinalAnswer_when_toolCallsReturned() throws IOException {
        // Given
        agent.setToolIds(List.of(100L));

        when(chatSessionMapper.selectById(1L)).thenReturn(session);
        when(agentService.getById(2L)).thenReturn(agent);
        when(chatMessageMapper.selectRecentBySessionId(anyLong(), anyInt())).thenReturn(Collections.emptyList());
        when(contextAssembler.assemble(any(), anyString(), anyString(), anyString(), any(), any())).thenReturn(new ArrayList<>());

        McpToolDefinition toolDef = new McpToolDefinition();
        toolDef.setId(100L);
        toolDef.setServerId(200L);
        toolDef.setName("getWeather");
        toolDef.setDescription("Get weather info");
        when(mcpToolService.getToolDefinitions(List.of(100L))).thenReturn(List.of(toolDef));

        ChatResponse firstResponse = new ChatResponse();
        firstResponse.setFinishReason("tool_calls");
        firstResponse.setContent(null);

        Map<String, Object> function = Map.of(
                "name", "getWeather",
                "arguments", "{\"city\":\"Beijing\"}"
        );
        Map<String, Object> toolCall = Map.of(
                "id", "call_1",
                "type", "function",
                "function", function
        );
        firstResponse.setToolCalls(List.of(toolCall));

        when(llmService.chat(anyLong(), any(ChatRequest.class))).thenReturn(firstResponse);
        when(objectMapper.readValue(anyString(), any(TypeReference.class))).thenReturn(Map.of("city", "Beijing"));
        when(mcpClientService.callTool(anyLong(), anyString(), any())).thenReturn("Sunny 25C");

        doAnswer(invocation -> {
            Consumer<String> onDelta = invocation.getArgument(2);
            Consumer<String> onFinish = invocation.getArgument(3);
            onDelta.accept("It is sunny.");
            onFinish.accept("stop");
            return null;
        }).when(llmService).streamChat(anyLong(), any(ChatRequest.class), any(), any());

        // When
        chatService.sendMessage(1L, request, emitter);

        // Then
        verify(llmService).chat(eq(10L), any(ChatRequest.class));
        verify(mcpClientService).callTool(200L, "getWeather", Map.of("city", "Beijing"));
        verify(llmService).streamChat(eq(10L), any(ChatRequest.class), any(), any());
    }

    @Test
    void should_returnWorkflowOutput_when_agentHasWorkflowId() throws IOException {
        // Given
        agent.setWorkflowId(99L);

        when(chatSessionMapper.selectById(1L)).thenReturn(session);
        when(agentService.getById(2L)).thenReturn(agent);
        when(workflowRunService.run(99L, "Hello")).thenReturn("Workflow result");

        // When
        chatService.sendMessage(1L, request, emitter);

        // Then
        verify(workflowRunService).run(99L, "Hello");
        verify(persistenceService).saveAssistantMessage(1L, "Workflow result", 4);
        verify(llmService, never()).streamChat(anyLong(), any(), any(), any());
    }

    @Test
    void should_appendRagContext_when_chunksRetrieved() throws IOException {
        // Given
        agent.setKnowledgeBaseId(50L);

        when(chatSessionMapper.selectById(1L)).thenReturn(session);
        when(agentService.getById(2L)).thenReturn(agent);
        when(chatMessageMapper.selectRecentBySessionId(anyLong(), anyInt())).thenReturn(Collections.emptyList());
        when(knowledgeRetrievalService.retrieve(50L, "Hello", 3, 0.35)).thenReturn(List.of("Chunk A", "Chunk B"));

        ArgumentCaptor<String> systemPromptCaptor = ArgumentCaptor.forClass(String.class);
        when(contextAssembler.assemble(any(), systemPromptCaptor.capture(), anyString(), anyString(), any(), any())).thenReturn(new ArrayList<>());

        doAnswer(invocation -> {
            Consumer<String> onFinish = invocation.getArgument(3);
            onFinish.accept("stop");
            return null;
        }).when(llmService).streamChat(anyLong(), any(ChatRequest.class), any(), any());

        // When
        chatService.sendMessage(1L, request, emitter);

        // Then
        String capturedPrompt = systemPromptCaptor.getValue();
        assertThat(capturedPrompt).contains("You are a helpful assistant.");
        assertThat(capturedPrompt).contains("Chunk A");
        assertThat(capturedPrompt).contains("Chunk B");
        assertThat(capturedPrompt).contains("请基于以下参考资料回答用户问题");
    }

    @Test
    void should_handleSessionNotFoundGracefully_when_sessionNotFound() {
        // Given
        when(chatSessionMapper.selectById(999L)).thenReturn(null);

        // When / Then: sendMessage catches all exceptions internally, no throw expected
        chatService.sendMessage(999L, request, emitter);

        verify(persistenceService, never()).saveUserMessage(anyLong(), anyString());
    }

    @Test
    void should_returnErrorMessage_when_toolCallThrowsException() throws IOException {
        // Given
        agent.setToolIds(List.of(100L));

        when(chatSessionMapper.selectById(1L)).thenReturn(session);
        when(agentService.getById(2L)).thenReturn(agent);
        when(chatMessageMapper.selectRecentBySessionId(anyLong(), anyInt())).thenReturn(Collections.emptyList());
        when(contextAssembler.assemble(any(), anyString(), anyString(), anyString(), any(), any())).thenReturn(new ArrayList<>());

        McpToolDefinition toolDef = new McpToolDefinition();
        toolDef.setId(100L);
        toolDef.setServerId(200L);
        toolDef.setName("getWeather");
        toolDef.setDescription("Get weather info");
        when(mcpToolService.getToolDefinitions(List.of(100L))).thenReturn(List.of(toolDef));

        ChatResponse firstResponse = new ChatResponse();
        firstResponse.setFinishReason("tool_calls");
        firstResponse.setContent(null);

        Map<String, Object> function = Map.of(
                "name", "getWeather",
                "arguments", "{\"city\":\"Beijing\"}"
        );
        Map<String, Object> toolCall = Map.of(
                "id", "call_1",
                "type", "function",
                "function", function
        );
        firstResponse.setToolCalls(List.of(toolCall));

        when(llmService.chat(anyLong(), any(ChatRequest.class))).thenReturn(firstResponse);
        when(objectMapper.readValue(anyString(), any(TypeReference.class))).thenReturn(Map.of("city", "Beijing"));
        when(mcpClientService.callTool(anyLong(), anyString(), any())).thenThrow(new RuntimeException("Connection refused"));

        doAnswer(invocation -> {
            Consumer<String> onDelta = invocation.getArgument(2);
            Consumer<String> onFinish = invocation.getArgument(3);
            onDelta.accept("Tool failed but chat continues.");
            onFinish.accept("stop");
            return null;
        }).when(llmService).streamChat(anyLong(), any(ChatRequest.class), any(), any());

        // When
        chatService.sendMessage(1L, request, emitter);

        // Then
        verify(mcpClientService).callTool(200L, "getWeather", Map.of("city", "Beijing"));
        verify(llmService).streamChat(eq(10L), any(ChatRequest.class), any(), any());
    }
}

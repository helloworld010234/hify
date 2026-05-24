package com.hify.modules.chat.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hify.common.exception.BizException;
import com.hify.common.exception.ErrorCode;
import com.hify.common.util.TokenUtil;
import com.hify.modules.agent.dto.response.AgentDetailResponse;
import com.hify.modules.agent.api.AgentService;
import com.hify.modules.chat.service.ChatService;
import com.hify.modules.chat.dto.request.ChatStreamRequest;
import com.hify.modules.chat.dto.response.ChatSessionResponse;
import com.hify.modules.chat.dto.response.ChatStreamEvent;
import com.hify.modules.chat.service.assembler.ChatContextAssembler;
import com.hify.modules.chat.entity.ChatMessage;
import com.hify.modules.knowledge.api.KnowledgeRetrievalService;
import com.hify.modules.chat.entity.ChatSession;
import com.hify.modules.chat.mapper.ChatMessageMapper;
import com.hify.modules.chat.mapper.ChatSessionMapper;
import com.hify.modules.mcp.api.McpClientService;
import com.hify.common.service.mcp.McpToolService;
import com.hify.common.service.mcp.McpToolDefinition;
import com.hify.common.util.MdcTaskWrapper;
import com.hify.modules.provider.api.LlmService;
import com.hify.modules.provider.dto.chat.ChatRequest;
import com.hify.modules.provider.dto.chat.ChatResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.concurrent.ThreadPoolExecutor;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 对话服务实现
 * <p>
 * 核心 SSE 流式逻辑，<strong>不声明 @Transactional</strong>，所有 DB 写操作委托给
 * {@link ChatPersistenceService} 的独立事务方法。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatServiceImpl implements ChatService {

    private static final String SESSION_TITLE_DEFAULT = "新会话";
    private static final String SESSION_TITLE_TRUNCATE_SUFFIX = "...";
    private static final int SESSION_TITLE_MAX_LEN = 30;

    private final AgentService agentService;
    private final ChatPersistenceService persistenceService;
    private final ChatContextAssembler contextAssembler;
    private final LlmService llmService;
    private final ChatSessionMapper chatSessionMapper;
    private final ChatMessageMapper chatMessageMapper;
    private final ObjectMapper objectMapper;
    private final KnowledgeRetrievalService knowledgeRetrievalService;
    private final McpToolService mcpToolService;
    private final McpClientService mcpClientService;
    private final ThreadPoolExecutor llmStreamExecutor;

    @Override
    public ChatSessionResponse createSession(Long agentId) {
        ChatSession session = persistenceService.getOrCreateSession(agentId, SESSION_TITLE_DEFAULT);
        ChatSessionResponse response = new ChatSessionResponse();
        response.setId(session.getId());
        response.setAgentId(session.getAgentId());
        response.setTitle(session.getTitle());
        return response;
    }

    @Override
    public void sendMessage(Long sessionId, ChatStreamRequest request, SseEmitter emitter) {
        AtomicBoolean cancelled = new AtomicBoolean(false);
        AtomicBoolean finished = new AtomicBoolean(false);
        long startTime = System.currentTimeMillis();

        emitter.onCompletion(() -> cancelled.set(true));
        emitter.onTimeout(() -> cancelled.set(true));
        emitter.onError(e -> cancelled.set(true));

        try {
            ChatSession session = validateSession(sessionId);
            AgentDetailResponse agent = validateAgent(session.getAgentId());

            persistenceService.saveUserMessage(sessionId, request.getMessage());

            List<ChatMessage> history = loadHistory(sessionId, agent);

            // 加载工具 schema（为空则和原有逻辑完全一致）
            ToolContext toolContext = loadToolSchemas(agent.getToolIds());

            StringBuilder contentBuilder = new StringBuilder();

            if (toolContext.schemas.isEmpty()) {
                // ====== 原有逻辑，完全不变 ======
                ChatRequest chatRequest = buildChatRequest(agent, history, request, null);
                llmService.streamChat(agent.getModelConfigId(), chatRequest,
                        delta -> handleDelta(emitter, contentBuilder, delta, cancelled),
                        finishReason -> handleFinish(emitter, session, request, contentBuilder,
                                finishReason, startTime, finished, cancelled));
            } else {
                // ====== 有工具：第一次同步调用（带 tools 参数）======
                ChatRequest firstRequest = buildChatRequest(agent, history, request, toolContext.schemas);
                ChatResponse firstResponse = llmService.chat(agent.getModelConfigId(), firstRequest);

                if ("tool_calls".equals(firstResponse.getFinishReason())) {
                    // 执行工具调用，构建 tool result 消息
                    List<com.hify.modules.provider.dto.chat.ChatMessage> toolMessages =
                            executeToolCalls(firstResponse.getToolCalls(), toolContext);

                    // 组装第二次调用的 messages
                    List<com.hify.modules.provider.dto.chat.ChatMessage> secondMessages = new ArrayList<>();
                    secondMessages.addAll(firstRequest.getMessages());

                    // assistant message with tool_calls
                    com.hify.modules.provider.dto.chat.ChatMessage assistantMsg =
                            new com.hify.modules.provider.dto.chat.ChatMessage();
                    assistantMsg.setRole("assistant");
                    assistantMsg.setContent(firstResponse.getContent());
                    assistantMsg.setToolCalls(firstResponse.getToolCalls());
                    secondMessages.add(assistantMsg);

                    // tool results
                    secondMessages.addAll(toolMessages);

                    // 第二次流式调用（把工具结果交给 LLM 生成最终回答）
                    ChatRequest secondRequest = new ChatRequest();
                    secondRequest.setMessages(secondMessages);
                    secondRequest.setTemperature(agent.getTemperature());
                    secondRequest.setMaxTokens(agent.getMaxTokens());

                    llmService.streamChat(agent.getModelConfigId(), secondRequest,
                            delta -> handleDelta(emitter, contentBuilder, delta, cancelled),
                            finishReason -> handleFinish(emitter, session, request, contentBuilder,
                                    finishReason, startTime, finished, cancelled));
                } else {
                    // LLM 直接回答了，没有调用工具，直接推送结果
                    String content = firstResponse.getContent();
                    if (content != null && !content.isEmpty()) {
                        contentBuilder.append(content);
                        sendEvent(emitter, ChatStreamEvent.delta(content));
                    }
                    handleFinish(emitter, session, request, contentBuilder,
                            "stop", startTime, finished, cancelled);
                }
            }

        } catch (IOException e) {
            handleIoException(emitter, e, cancelled, startTime);
        } catch (Exception e) {
            handleUnexpectedException(emitter, e, startTime);
        }
    }

    private ChatSession validateSession(Long sessionId) {
        ChatSession session = chatSessionMapper.selectById(sessionId);
        if (session == null || session.getDeleted() != null && session.getDeleted() == 1) {
            throw new BizException(ErrorCode.CHAT_SESSION_NOT_FOUND, "会话不存在或已删除");
        }
        return session;
    }

    private AgentDetailResponse validateAgent(Long agentId) {
        AgentDetailResponse agent = agentService.getById(agentId);
        if (agent == null) {
            throw new BizException(ErrorCode.AGENT_NOT_FOUND, "Agent 不存在");
        }
        return agent;
    }

    private List<ChatMessage> loadHistory(Long sessionId, AgentDetailResponse agent) {
        int limit = (agent.getMaxContextTurns() != null ? agent.getMaxContextTurns() : 10) * 2 + 1;
        return chatMessageMapper.selectRecentBySessionId(sessionId, limit);
    }

    private ChatRequest buildChatRequest(AgentDetailResponse agent, List<ChatMessage> history,
                                          ChatStreamRequest request,
                                          List<Map<String, Object>> toolSchemas) {
        String systemPrompt = buildSystemPromptWithRag(agent, request.getMessage());

        ChatRequest chatRequest = new ChatRequest();
        chatRequest.setMessages(contextAssembler.assemble(
                history,
                systemPrompt,
                request.getMessage(),
                request.getContextStrategy(),
                agent.getMaxTokens(),
                agent.getMaxContextTurns()
        ));
        chatRequest.setTemperature(agent.getTemperature());
        chatRequest.setMaxTokens(agent.getMaxTokens());
        if (toolSchemas != null && !toolSchemas.isEmpty()) {
            chatRequest.setTools(toolSchemas);
        }
        return chatRequest;
    }

    /**
     * 构建带 RAG 检索结果的 System Prompt
     */
    private String buildSystemPromptWithRag(AgentDetailResponse agent, String userMessage) {
        String originalPrompt = agent.getSystemPrompt();

        // 检查 Agent 是否绑定了知识库
        Long kbId = agent.getKnowledgeBaseId();
        if (kbId == null) {
            log.debug("RAG skipped: agentId={}, knowledgeBaseId is null", agent.getId());
            return originalPrompt;
        }

        // 检索相关知识库分块
        List<String> chunks = knowledgeRetrievalService.retrieve(kbId, userMessage, 3, 0.35);
        log.debug("RAG retrieve result: agentId={}, kbId={}, chunks={}", agent.getId(), kbId, chunks.size());
        if (chunks.isEmpty()) {
            return originalPrompt;
        }

        // 拼接 RAG 提示词
        StringBuilder sb = new StringBuilder();
        if (originalPrompt != null && !originalPrompt.isBlank()) {
            sb.append(originalPrompt);
        }
        sb.append("\n\n请基于以下参考资料回答用户问题。\n");
        sb.append("如果资料中没有相关信息，直接说\"我没有找到相关资料\"，不要编造。\n\n");
        sb.append("【参考资料】\n");
        for (int i = 0; i < chunks.size(); i++) {
            sb.append("[").append(i + 1).append("] ").append(chunks.get(i)).append("\n");
        }

        return sb.toString();
    }

    // ==================== MCP 工具调用相关 ====================

    /**
     * 加载 Agent 绑定工具的 OpenAI tools schema
     */
    private ToolContext loadToolSchemas(List<Long> toolIds) {
        ToolContext ctx = new ToolContext();
        if (toolIds == null || toolIds.isEmpty()) {
            return ctx;
        }
        List<McpToolDefinition> tools = mcpToolService.getToolDefinitions(toolIds);
        for (McpToolDefinition tool : tools) {
            Map<String, Object> function = new HashMap<>();
            function.put("name", tool.getName());
            function.put("description", tool.getDescription());
            if (tool.getInputSchema() != null) {
                function.put("parameters", objectMapper.convertValue(tool.getInputSchema(), new TypeReference<Map<String, Object>>() {}));
            } else {
                function.put("parameters", Map.of("type", "object"));
            }
            Map<String, Object> toolObj = new HashMap<>();
            toolObj.put("type", "function");
            toolObj.put("function", function);
            ctx.schemas.add(toolObj);
            ctx.nameToServerId.put(tool.getName(), tool.getServerId());
        }
        return ctx;
    }

    /**
     * 解析并执行 tool_calls，返回 role=tool 的消息列表
     */
    private List<com.hify.modules.provider.dto.chat.ChatMessage> executeToolCalls(
            List<Map<String, Object>> toolCalls, ToolContext toolContext) {
        List<com.hify.modules.provider.dto.chat.ChatMessage> resultMessages = new ArrayList<>();
        if (toolCalls == null || toolCalls.isEmpty()) {
            return resultMessages;
        }

        for (Map<String, Object> toolCall : toolCalls) {
            String id = (String) toolCall.get("id");
            Map<String, Object> function = (Map<String, Object>) toolCall.get("function");
            String name = (String) function.get("name");
            String argumentsJson = (String) function.get("arguments");

            Long serverId = toolContext.nameToServerId.get(name);
            String toolResult;
            try {
                Map<String, Object> arguments = objectMapper.readValue(argumentsJson, new TypeReference<Map<String, Object>>() {});
                toolResult = mcpClientService.callTool(serverId, name, arguments);
            } catch (Exception e) {
                log.error("MCP tool call failed in chat: name={}, serverId={}", name, serverId, e);
                toolResult = "工具调用失败: " + e.getMessage();
            }

            com.hify.modules.provider.dto.chat.ChatMessage toolMsg =
                    new com.hify.modules.provider.dto.chat.ChatMessage();
            toolMsg.setRole("tool");
            toolMsg.setToolCallId(id);
            toolMsg.setContent(toolResult);
            resultMessages.add(toolMsg);
        }
        return resultMessages;
    }

    /**
     * 工具上下文（schema + name->serverId 映射）
     */
    private static class ToolContext {
        final List<Map<String, Object>> schemas = new ArrayList<>();
        final Map<String, Long> nameToServerId = new HashMap<>();
    }

    // ==================== SSE 事件处理 ====================

    private void handleDelta(SseEmitter emitter, StringBuilder contentBuilder,
                             String delta, AtomicBoolean cancelled) {
        if (cancelled.get()) {
            return;
        }
        contentBuilder.append(delta);
        sendEvent(emitter, ChatStreamEvent.delta(delta));
    }

    private void handleFinish(SseEmitter emitter, ChatSession session, ChatStreamRequest request,
                              StringBuilder contentBuilder, String finishReason,
                              long startTime, AtomicBoolean finished, AtomicBoolean cancelled) {
        if (!finished.compareAndSet(false, true)) {
            return;
        }
        if (cancelled.get()) {
            return;
        }
        long latency = System.currentTimeMillis() - startTime;
        String fullContent = contentBuilder.toString();
        int tokens = TokenUtil.estimateTokens(fullContent);
        persistenceService.saveAssistantMessage(session.getId(), fullContent, tokens);

        if (SESSION_TITLE_DEFAULT.equals(session.getTitle())) {
            String title = truncateTitle(request.getMessage());
            persistenceService.updateSessionTitle(session.getId(), title);
        }

        sendEvent(emitter, ChatStreamEvent.done(finishReason, latency));
        completeEmitter(emitter);
    }

    private void handleIoException(SseEmitter emitter, IOException e,
                                   AtomicBoolean cancelled, long startTime) {
        if (cancelled.get()) {
            log.info("Stream aborted by client disconnect");
            return;
        }
        log.error("LLM stream IO error", e);
        sendEvent(emitter, ChatStreamEvent.error("LLM_TIMEOUT", "LLM 响应超时或网络异常"));
        completeEmitterWithError(emitter, new BizException(ErrorCode.INTERNAL_ERROR, "LLM 响应超时"));
    }

    private void handleUnexpectedException(SseEmitter emitter, Exception e, long startTime) {
        log.error("Chat stream unexpected error", e);
        sendEvent(emitter, ChatStreamEvent.error("INTERNAL_ERROR", "服务器内部错误"));
        completeEmitterWithError(emitter, new BizException(ErrorCode.INTERNAL_ERROR, "服务器内部错误"));
    }

    private void sendEvent(SseEmitter emitter, ChatStreamEvent event) {
        try {
            String json = objectMapper.writeValueAsString(event);
            emitter.send(SseEmitter.event().data(json));
        } catch (IOException e) {
            log.warn("SSE send failed, client may have disconnected");
        }
    }

    private void completeEmitter(SseEmitter emitter) {
        try {
            emitter.complete();
        } catch (Exception e) {
            log.warn("SSE complete failed, client may have disconnected");
        }
    }

    private void completeEmitterWithError(SseEmitter emitter, Exception ex) {
        try {
            emitter.completeWithError(ex);
        } catch (Exception e) {
            log.warn("SSE completeWithError failed, client may have disconnected");
        }
    }

    private String truncateTitle(String message) {
        if (message.length() <= SESSION_TITLE_MAX_LEN) {
            return message;
        }
        return message.substring(0, SESSION_TITLE_MAX_LEN) + SESSION_TITLE_TRUNCATE_SUFFIX;
    }

    @Override
    public SseEmitter createStreamEmitter(Long sessionId, String message) {
        SseEmitter emitter = new SseEmitter(300_000L); // 5 分钟超时
        ChatStreamRequest request = new ChatStreamRequest();
        request.setMessage(message);
        llmStreamExecutor.execute(MdcTaskWrapper.wrap(() -> {
            try {
                sendMessage(sessionId, request, emitter);
            } catch (Exception e) {
                log.error("LLM stream task failed", e);
                completeEmitterWithError(emitter, e);
            }
        }));
        return emitter;
    }
}

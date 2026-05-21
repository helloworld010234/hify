package com.hify.modules.chat.domain.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hify.common.exception.BizException;
import com.hify.common.exception.ErrorCode;
import com.hify.common.util.TokenUtil;
import com.hify.modules.agent.api.dto.response.AgentDetailResponse;
import com.hify.modules.agent.domain.service.AgentService;
import com.hify.modules.chat.api.ChatService;
import com.hify.modules.chat.api.dto.request.ChatStreamRequest;
import com.hify.modules.chat.api.dto.response.ChatSessionResponse;
import com.hify.modules.chat.api.dto.response.ChatStreamEvent;
import com.hify.modules.chat.domain.assembler.ChatContextAssembler;
import com.hify.modules.chat.infra.entity.ChatMessage;
import com.hify.modules.knowledge.api.KnowledgeRetrievalService;
import com.hify.modules.chat.infra.entity.ChatSession;
import com.hify.modules.chat.infra.mapper.ChatMessageMapper;
import com.hify.modules.chat.infra.mapper.ChatSessionMapper;
import com.hify.modules.provider.api.LlmService;
import com.hify.modules.provider.api.dto.chat.ChatRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
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
            ChatRequest chatRequest = buildChatRequest(agent, history, request);

            StringBuilder contentBuilder = new StringBuilder();

            llmService.streamChat(agent.getModelConfigId(), chatRequest,
                    delta -> handleDelta(emitter, contentBuilder, delta, cancelled),
                    finishReason -> handleFinish(emitter, session, request, contentBuilder,
                            finishReason, startTime, finished, cancelled));

        } catch (IOException e) {
            handleIoException(emitter, e, cancelled, startTime);
        } catch (Exception e) {
            handleUnexpectedException(emitter, e, startTime);
        }
    }

    private ChatSession validateSession(Long sessionId) {
        ChatSession session = chatSessionMapper.selectById(sessionId);
        if (session == null || session.getDeleted() != null && session.getDeleted() == 1) {
            throw new BizException(ErrorCode.NOT_FOUND, "会话不存在或已删除");
        }
        return session;
    }

    private AgentDetailResponse validateAgent(Long agentId) {
        AgentDetailResponse agent = agentService.getById(agentId);
        if (agent == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "Agent 不存在");
        }
        return agent;
    }

    private List<ChatMessage> loadHistory(Long sessionId, AgentDetailResponse agent) {
        int limit = (agent.getMaxContextTurns() != null ? agent.getMaxContextTurns() : 10) * 2 + 1;
        return chatMessageMapper.selectRecentBySessionId(sessionId, limit);
    }

    private ChatRequest buildChatRequest(AgentDetailResponse agent, List<ChatMessage> history,
                                          ChatStreamRequest request) {
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
}

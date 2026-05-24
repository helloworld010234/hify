package com.hify.modules.chat.service.impl;

import com.hify.common.util.TokenUtil;
import com.hify.modules.chat.entity.ChatMessage;
import com.hify.modules.chat.entity.ChatSession;
import com.hify.modules.chat.mapper.ChatMessageMapper;
import com.hify.modules.chat.mapper.ChatSessionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 对话持久化服务
 * <p>
 * 所有 DB 写操作独立事务，避免 SSE 长连接期间占用数据库连接。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatPersistenceService {

    private final ChatSessionMapper chatSessionMapper;
    private final ChatMessageMapper chatMessageMapper;

    @Transactional(rollbackFor = Exception.class)
    public ChatSession getOrCreateSession(Long agentId, String title) {
        ChatSession session = new ChatSession();
        session.setAgentId(agentId);
        session.setTitle(title != null ? title : "新会话");
        chatSessionMapper.insert(session);
        return session;
    }

    @Transactional(rollbackFor = Exception.class)
    public void saveUserMessage(Long sessionId, String content) {
        ChatMessage msg = new ChatMessage();
        msg.setSessionId(sessionId);
        msg.setRole("user");
        msg.setContent(content);
        msg.setTokens(TokenUtil.estimateTokens(content));
        chatMessageMapper.insert(msg);
    }

    @Transactional(rollbackFor = Exception.class)
    public void saveAssistantMessage(Long sessionId, String content, Integer tokens) {
        ChatMessage msg = new ChatMessage();
        msg.setSessionId(sessionId);
        msg.setRole("assistant");
        msg.setContent(content);
        msg.setTokens(tokens);
        chatMessageMapper.insert(msg);
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateSessionTitle(Long sessionId, String title) {
        ChatSession session = new ChatSession();
        session.setId(sessionId);
        session.setTitle(title);
        chatSessionMapper.updateById(session);
    }


}

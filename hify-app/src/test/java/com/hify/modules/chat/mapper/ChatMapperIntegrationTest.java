package com.hify.modules.chat.mapper;

import com.hify.AbstractIntegrationTest;
import com.hify.modules.chat.entity.ChatMessage;
import com.hify.modules.chat.entity.ChatSession;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.jdbc.Sql;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ChatMapperIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private ChatSessionMapper chatSessionMapper;

    @Autowired
    private ChatMessageMapper chatMessageMapper;

    @Test
    @DisplayName("P0-1: ChatSession Mapper — 插入后 JDBC 能查到数据")
    void chatSession_insertAndSelect_shouldPersist() {
        ChatSession session = new ChatSession();
        session.setAgentId(6L);
        session.setTitle("退货咨询");
        session.setUserId(1L);

        chatSessionMapper.insert(session);
        assertNotNull(session.getId());
        assertTrue(session.getId() > 0);

        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT * FROM t_chat_session WHERE id = ?", session.getId());
        assertEquals(6L, ((Number) row.get("agent_id")).longValue());
        assertEquals("退货咨询", row.get("title"));
        assertEquals(1L, ((Number) row.get("user_id")).longValue());
        assertEquals(0, ((Number) row.get("deleted")).intValue());
    }

    @Test
    @DisplayName("P0-1: ChatMessage Mapper — 批量插入后 selectRecentBySessionId 按时间正序返回")
    void chatMessage_insertMultiple_selectRecent_shouldReturnOrdered() {
        Long sessionId = 100L;

        ChatMessage msg1 = new ChatMessage();
        msg1.setSessionId(sessionId);
        msg1.setRole("user");
        msg1.setContent("订单怎么查？");
        msg1.setTokens(5);
        chatMessageMapper.insert(msg1);

        ChatMessage msg2 = new ChatMessage();
        msg2.setSessionId(sessionId);
        msg2.setRole("assistant");
        msg2.setContent("您可以登录官网查询。");
        msg2.setTokens(8);
        chatMessageMapper.insert(msg2);

        List<ChatMessage> recent = chatMessageMapper.selectRecentBySessionId(sessionId, 10);
        assertEquals(2, recent.size());
        assertEquals("user", recent.get(0).getRole());
        assertEquals("assistant", recent.get(1).getRole());
        assertEquals("订单怎么查？", recent.get(0).getContent());
    }

    @Test
    @DisplayName("P0-1: ChatMessage Mapper — selectRecentBySessionId 只返回未删除数据")
    void chatMessage_deletedRows_shouldBeExcluded() {
        Long sessionId = 200L;

        ChatMessage msg = new ChatMessage();
        msg.setSessionId(sessionId);
        msg.setRole("user");
        msg.setContent("test");
        chatMessageMapper.insert(msg);

        // 逻辑删除
        chatMessageMapper.deleteById(msg.getId());

        List<ChatMessage> recent = chatMessageMapper.selectRecentBySessionId(sessionId, 10);
        assertTrue(recent.isEmpty());

        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT * FROM t_chat_message WHERE id = ?", msg.getId());
        assertEquals(1, ((Number) row.get("deleted")).intValue());
    }
}

package com.hify.modules.chat.service;

import com.hify.AbstractIntegrationTest;
import com.hify.modules.chat.service.ChatService;
import com.hify.modules.chat.dto.response.ChatSessionResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.jdbc.Sql;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ChatServiceIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private ChatService chatService;

    @Test
    @DisplayName("P0-3: createSession — 创建会话后 JDBC 能查到数据")
    void createSession_shouldPersistToDb() {
        ChatSessionResponse session = chatService.createSession(6L);

        assertNotNull(session);
        assertNotNull(session.getId());
        assertTrue(session.getId() > 0);
        assertEquals(6L, session.getAgentId());
        assertEquals("新会话", session.getTitle());

        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT * FROM t_chat_session WHERE id = ?", session.getId());
        assertEquals(6L, ((Number) row.get("agent_id")).longValue());
        assertEquals("新会话", row.get("title"));
        assertEquals(0, ((Number) row.get("deleted")).intValue());
    }
}

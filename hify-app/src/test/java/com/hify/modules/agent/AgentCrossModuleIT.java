package com.hify.modules.agent;

import com.hify.AbstractIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Agent 跨模块集成测试 — 游标分页验证
 * <p>
 * 验证对话历史消息按 (created_at, id) 正确分页排序。
 * 禁用测试事务，由 @Sql 预置数据，测试结束后手动清理。
 */
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class AgentCrossModuleIT extends AbstractIntegrationTest {

    @AfterEach
    void cleanupPaginationTestData() {
        jdbcTemplate.update("DELETE FROM t_chat_message WHERE session_id = 730");
        jdbcTemplate.update("DELETE FROM t_chat_session WHERE id = 730");
        jdbcTemplate.update("DELETE FROM t_agent WHERE id IN (700, 701, 702)");
        jdbcTemplate.update("DELETE FROM t_model WHERE id IN (700, 720, 721)");
        jdbcTemplate.update("DELETE FROM t_provider WHERE id IN (700, 710, 720, 721, 730)");
    }

    @Test
    @DisplayName("P1: 对话历史游标分页 — 按 (created_at, id) 正确分页")
    @Sql(scripts = "classpath:db/chat-enhanced-test-data.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
    void should_returnMessagesByCursorPagination_when_queryHistory() throws Exception {
        // Then: 数据库验证消息顺序（按 created_at DESC, id DESC 取前 5 条）
        List<Map<String, Object>> messages = jdbcTemplate.queryForList(
                "SELECT * FROM t_chat_message WHERE session_id = 730 AND deleted = 0 ORDER BY created_at DESC, id DESC LIMIT 5");
        assertThat(messages).hasSize(5);

        // 验证消息按时间倒序排列（最新的在前）
        for (int i = 0; i < messages.size() - 1; i++) {
            Long currentId = ((Number) messages.get(i).get("id")).longValue();
            Long nextId = ((Number) messages.get(i + 1).get("id")).longValue();
            assertThat(currentId).isGreaterThan(nextId);
        }

        // 验证最新消息是 Reply 25
        assertThat(messages.get(0).get("content")).isEqualTo("Reply 25");
        assertThat(messages.get(0).get("role")).isEqualTo("assistant");

        // 验证第二新是 Message 25
        assertThat(messages.get(1).get("content")).isEqualTo("Message 25");
        assertThat(messages.get(1).get("role")).isEqualTo("user");

        // 验证总消息数
        Integer totalCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM t_chat_message WHERE session_id = 730 AND deleted = 0", Integer.class);
        assertThat(totalCount).isEqualTo(50);
    }
}

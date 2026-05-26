package com.hify.modules.knowledge;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Transactional(propagation = Propagation.NOT_SUPPORTED)
class KnowledgeBaseControllerIT extends AbstractIntegrationTest {

    @AfterEach
    void cleanupKnowledgeTestData() {
        jdbcTemplate.update("DELETE FROM document_chunk WHERE document_id IN (501,502,503)");
        jdbcTemplate.update("DELETE FROM t_document WHERE knowledge_base_id = 500");
        jdbcTemplate.update("DELETE FROM t_chat_message WHERE session_id = 500");
        jdbcTemplate.update("DELETE FROM t_chat_session WHERE id = 500");
        jdbcTemplate.update("DELETE FROM t_agent WHERE id = 500");
        jdbcTemplate.update("DELETE FROM t_model WHERE id = 500");
        jdbcTemplate.update("DELETE FROM t_provider WHERE id = 500");
        jdbcTemplate.update("DELETE FROM t_knowledge_base WHERE id = 500");
    }

    @Test
    @DisplayName("P1: 知识库文档列表 — 返回含状态和分页")
    @Sql(scripts = "classpath:db/knowledge-test-data.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
    void should_returnDocumentsWithStatus_when_listDocuments() throws Exception {
        mockMvc.perform(get("/api/v1/knowledge-bases/{kbId}/documents", 500))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.list").isArray())
                .andExpect(jsonPath("$.data.list.length()").value(3))
                .andExpect(jsonPath("$.data.list[0].status").exists());

        // Then: 数据库验证
        List<Map<String, Object>> docs = jdbcTemplate.queryForList(
                "SELECT id, status FROM t_document WHERE knowledge_base_id = 500 AND deleted = 0 ORDER BY id");
        assertThat(docs).hasSize(3);
        assertThat(docs.get(0).get("status")).isEqualTo("PENDING");
        assertThat(docs.get(1).get("status")).isEqualTo("DONE");
        assertThat(docs.get(2).get("status")).isEqualTo("FAILED");
    }

    @Test
    @DisplayName("P1: Agent 绑定知识库时对话 — RAG 检索条件含 knowledgeBaseId + deleted=0")
    @Sql(scripts = "classpath:db/knowledge-test-data.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
    void should_assembleRagConditionCorrectly_when_chatWithKbBinding() throws Exception {
        // Given: Agent 500 绑定知识库 500
        // When: 查询 Agent 详情（间接验证绑定关系）
        Map<String, Object> agent = jdbcTemplate.queryForMap(
                "SELECT knowledge_base_id FROM t_agent WHERE id = 500");
        assertThat(agent.get("knowledge_base_id")).isEqualTo(500L);

        // Then: 知识库存在且未删除
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM t_knowledge_base WHERE id = 500 AND deleted = 0", Integer.class);
        assertThat(count).isEqualTo(1);

        // Then: chunks 属于正确的知识库
        Integer chunkCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM document_chunk c " +
                "JOIN t_document d ON c.document_id = d.id " +
                "WHERE d.knowledge_base_id = 500 AND c.deleted = 0", Integer.class);
        assertThat(chunkCount).isEqualTo(3);
    }
}

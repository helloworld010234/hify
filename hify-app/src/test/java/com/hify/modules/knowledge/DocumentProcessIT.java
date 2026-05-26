package com.hify.modules.knowledge;

import com.hify.AbstractIntegrationTest;
import com.hify.modules.knowledge.client.EmbeddingClient;
import com.hify.modules.knowledge.pg.DocumentChunkRepository;
import com.hify.modules.provider.api.ProviderService;
import com.hify.modules.provider.dto.response.ProviderDetailResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Transactional(propagation = Propagation.NOT_SUPPORTED)
class DocumentProcessIT extends AbstractIntegrationTest {

    @MockBean
    private EmbeddingClient embeddingClient;

    @MockBean
    private ProviderService providerService;

    @MockBean
    private DocumentChunkRepository documentChunkRepository;

    @AfterEach
    void cleanupDocumentTestData() {
        jdbcTemplate.update("DELETE FROM document_chunk WHERE document_id IN (SELECT id FROM t_document WHERE knowledge_base_id = 500)");
        jdbcTemplate.update("DELETE FROM t_document WHERE knowledge_base_id = 500");
        jdbcTemplate.update("DELETE FROM t_chat_message WHERE session_id = 500");
        jdbcTemplate.update("DELETE FROM t_chat_session WHERE id = 500");
        jdbcTemplate.update("DELETE FROM t_agent WHERE id = 500");
        jdbcTemplate.update("DELETE FROM t_model WHERE id = 500");
        jdbcTemplate.update("DELETE FROM t_provider WHERE id IN (500, 1)");
        jdbcTemplate.update("DELETE FROM t_knowledge_base WHERE id = 500");
    }

    @Test
    @DisplayName("P0: 文档上传 — 创建 PENDING 状态记录")
    @Sql(scripts = "classpath:db/knowledge-test-data.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
    void should_createDocumentRecordWithPendingStatus_when_upload() throws Exception {
        // Given: Mock ProviderService、EmbeddingClient（延迟返回，给测试留出查询时间）、DocumentChunkRepository
        ProviderDetailResponse provider = new ProviderDetailResponse();
        provider.setId(1L);
        provider.setBaseUrl("https://api.test.com");
        when(providerService.getById(anyLong())).thenReturn(provider);
        when(providerService.getApiKey(anyLong())).thenReturn("test-api-key");
        doAnswer(invocation -> {
            Thread.sleep(3000); // 延迟 3 秒，确保测试能在异步任务完成前查询到 PENDING
            return List.of(new float[]{0.1f, 0.2f, 0.3f});
        }).when(embeddingClient).embedBatch(anyList(), anyString(), anyString(), anyString());
        doNothing().when(documentChunkRepository).batchInsert(anyList());

        MockMultipartFile file = new MockMultipartFile(
                "file", "test.txt", "text/plain", "Hello world content".getBytes());

        mockMvc.perform(multipart("/api/v1/knowledge-bases/{kbId}/documents", 500)
                        .file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        // Then: 数据库有文档记录，状态为 PENDING 或 PROCESSING（异步任务可能已启动）
        List<Map<String, Object>> docs = jdbcTemplate.queryForList(
                "SELECT * FROM t_document WHERE knowledge_base_id = 500 AND name = 'test.txt'");
        assertThat(docs).hasSize(1);
        assertThat(docs.get(0).get("status")).isIn("PENDING", "PROCESSING");
        assertThat(docs.get(0).get("file_type")).isEqualTo("txt");
    }

    @Test
    @DisplayName("P0: 异步文档处理完成 — 状态变为 DONE")
    @Sql(scripts = "classpath:db/knowledge-test-data.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
    void should_transitionToDone_when_processDocumentCompletes() throws Exception {
        // Given: Mock ProviderService、EmbeddingClient、DocumentChunkRepository
        ProviderDetailResponse provider = new ProviderDetailResponse();
        provider.setId(1L);
        provider.setBaseUrl("https://api.test.com");
        when(providerService.getById(anyLong())).thenReturn(provider);
        when(providerService.getApiKey(anyLong())).thenReturn("test-api-key");
        when(embeddingClient.embedBatch(anyList(), anyString(), anyString(), anyString())).thenReturn(List.of(
                new float[]{0.1f, 0.2f, 0.3f},
                new float[]{0.4f, 0.5f, 0.6f}
        ));
        doNothing().when(documentChunkRepository).batchInsert(anyList());

        MockMultipartFile file = new MockMultipartFile(
                "file", "process.txt", "text/plain", "Short text for processing".getBytes());

        mockMvc.perform(multipart("/api/v1/knowledge-bases/{kbId}/documents", 500)
                        .file(file))
                .andExpect(status().isOk());

        // Then: 轮询验证状态变为 DONE（使用 Thread.sleep 替代 Awaitility）
        String status = null;
        for (int i = 0; i < 30; i++) {
            TimeUnit.MILLISECONDS.sleep(500);
            List<String> statuses = jdbcTemplate.queryForList(
                    "SELECT status FROM t_document WHERE knowledge_base_id = 500 AND name = 'process.txt'",
                    String.class);
            if (!statuses.isEmpty()) {
                status = statuses.get(0);
                if ("DONE".equals(status) || "FAILED".equals(status)) {
                    break;
                }
            }
        }
        assertThat(status).isEqualTo("DONE");
    }

    @Test
    @DisplayName("P0: Embedding 异常 — 文档状态变为 FAILED")
    @Sql(scripts = "classpath:db/knowledge-test-data.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
    void should_transitionToFailed_when_embedChunksThrows() throws Exception {
        // Given: Mock ProviderService 返回有效 Provider，Mock EmbeddingClient 抛异常
        ProviderDetailResponse provider = new ProviderDetailResponse();
        provider.setId(1L);
        provider.setBaseUrl("https://api.test.com");
        when(providerService.getById(anyLong())).thenReturn(provider);
        when(providerService.getApiKey(anyLong())).thenReturn("test-api-key");
        doNothing().when(documentChunkRepository).batchInsert(anyList());
        when(embeddingClient.embedBatch(anyList(), anyString(), anyString(), anyString()))
                .thenThrow(new RuntimeException("Embedding service unavailable"));

        MockMultipartFile file = new MockMultipartFile(
                "file", "fail.txt", "text/plain", "Text that will fail".getBytes());

        mockMvc.perform(multipart("/api/v1/knowledge-bases/{kbId}/documents", 500)
                        .file(file))
                .andExpect(status().isOk());

        // Then: 轮询验证状态变为 FAILED（使用 Thread.sleep 替代 Awaitility）
        String status = null;
        for (int i = 0; i < 30; i++) {
            TimeUnit.MILLISECONDS.sleep(500);
            List<String> statuses = jdbcTemplate.queryForList(
                    "SELECT status FROM t_document WHERE knowledge_base_id = 500 AND name = 'fail.txt'",
                    String.class);
            if (!statuses.isEmpty()) {
                status = statuses.get(0);
                if ("FAILED".equals(status)) {
                    break;
                }
            }
        }
        assertThat(status).isEqualTo("FAILED");
    }
}

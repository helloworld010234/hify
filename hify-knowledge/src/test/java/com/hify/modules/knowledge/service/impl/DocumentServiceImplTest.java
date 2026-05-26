package com.hify.modules.knowledge.service.impl;


import com.hify.modules.knowledge.dto.ChunkDTO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DocumentServiceImpl 单元测试
 */
@ExtendWith(MockitoExtension.class)
class DocumentServiceImplTest {

    private final DocumentServiceImpl documentService = new DocumentServiceImpl(
            null, null, null, null, null, null
    );

    // ==================== splitChunks ====================

    @Test
    void should_splitByParagraphBoundary_when_textHasParagraphs() {
        // Given
        String text = "第一段内容。\n\n第二段内容。\n\n第三段内容。";

        // When
        @SuppressWarnings("unchecked")
        List<ChunkDTO> chunks = (List<ChunkDTO>) ReflectionTestUtils.invokeMethod(
                documentService, "splitChunks", text
        );

        // Then
        assertThat(chunks).isNotNull();
        assertThat(chunks).hasSizeGreaterThanOrEqualTo(1);
        assertThat(chunks.get(0).getContent()).contains("第一段内容");
    }

    @Test
    void should_fallbackToCharTruncation_when_noNaturalBoundary() {
        // Given
        String text = "abcdefghij".repeat(200); // 2000 chars, no punctuation or paragraph

        // When
        @SuppressWarnings("unchecked")
        List<ChunkDTO> chunks = (List<ChunkDTO>) ReflectionTestUtils.invokeMethod(
                documentService, "splitChunks", text
        );

        // Then
        assertThat(chunks).isNotNull();
        assertThat(chunks).hasSizeGreaterThan(1);
        // Each chunk should be non-empty
        for (ChunkDTO chunk : chunks) {
            assertThat(chunk.getContent()).isNotBlank();
        }
    }

    @Test
    void should_forceAdvance_when_overlapEqualsChunkSize() {
        // Given: a very short text where overlap logic could cause nextPos <= pos
        // CHUNK_SIZE_TOKENS=512, CHUNK_OVERLAP_TOKENS=64, but with tiny text it should still advance
        String text = "a";

        // When
        @SuppressWarnings("unchecked")
        List<ChunkDTO> chunks = (List<ChunkDTO>) ReflectionTestUtils.invokeMethod(
                documentService, "splitChunks", text
        );

        // Then
        assertThat(chunks).isNotNull();
        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0).getContent()).isEqualTo("a");
    }

    // ==================== findChunkEnd ====================

    @Test
    void should_findParagraphBoundary_when_withinTokenLimit() {
        // Given
        String text = "Hello world.\n\nNext paragraph.";
        int start = 0;
        int maxTokens = 512;

        // When
        Integer end = (Integer) ReflectionTestUtils.invokeMethod(
                documentService, "findChunkEnd", text, start, maxTokens
        );

        // Then
        assertThat(end).isNotNull();
        assertThat(end).isGreaterThan(start);
        // Should stop at or after the paragraph break
        assertThat(end).isLessThanOrEqualTo(text.length());
    }

    // ==================== estimateCharsForTokens ====================

    @Test
    void should_returnZero_when_startEqualsEnd() {
        // Given
        String text = "any";
        int start = 5;
        int end = 5;
        int targetTokens = 10;

        // When
        Integer result = (Integer) ReflectionTestUtils.invokeMethod(
                documentService, "estimateCharsForTokens", text, start, end, targetTokens
        );

        // Then
        assertThat(result).isEqualTo(0);
    }

    @Test
    void should_estimateCharsCorrectly_forShortText() {
        // Given
        String text = "hello world";
        int start = 0;
        int end = text.length();
        int targetTokens = 2; // "hello world" is ~2 tokens (english words)

        // When
        Integer result = (Integer) ReflectionTestUtils.invokeMethod(
                documentService, "estimateCharsForTokens", text, start, end, targetTokens
        );

        // Then
        assertThat(result).isNotNull();
        assertThat(result).isGreaterThanOrEqualTo(0);
        assertThat(result).isLessThanOrEqualTo(end - start);
    }
}

package com.hify.common.util;

import com.hify.common.test.AbstractUnitTest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TokenUtil 单元测试
 */
class TokenUtilTest extends AbstractUnitTest {

    @Test
    void should_returnZero_when_textIsNullOrEmpty() {
        // Given & When & Then
        assertThat(TokenUtil.estimateTokens(null)).isEqualTo(0);
        assertThat(TokenUtil.estimateTokens("")).isEqualTo(0);
    }

    @Test
    void should_countEnglishWordsCorrectly() {
        // Given
        String text = "Hello world this is a test";

        // When
        int tokens = TokenUtil.estimateTokens(text);

        // Then
        // 21 non-whitespace chars * 0.3 = 6.3 -> rounded to 6
        assertThat(tokens).isEqualTo(6);
    }

    @Test
    void should_countChineseCharsCorrectly() {
        // Given
        String text = "你好世界";

        // When
        int tokens = TokenUtil.estimateTokens(text);

        // Then
        // 4 chinese chars * 1.5 = 6 -> rounded to 6
        assertThat(tokens).isEqualTo(6);
    }
}

package com.hify.common.service;

import com.hify.common.test.AbstractUnitTest;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * EncryptionService 单元测试
 */
class EncryptionServiceTest extends AbstractUnitTest {

    private final EncryptionService encryptionService = new EncryptionService("HifyDefaultKey16");

    @Test
    void should_decryptToOriginal_when_encryptWithDefaultKey() {
        // Given
        String original = "Hello Hify";

        // When
        String cipher = encryptionService.encrypt(original);
        String decrypted = encryptionService.decrypt(cipher);

        // Then
        assertThat(decrypted).isEqualTo(original);
    }

    @Test
    void should_handleNullAndBlankGracefully() {
        // Given & When & Then
        assertThat(encryptionService.encrypt(null)).isNull();
        assertThat(encryptionService.encrypt("")).isEqualTo("");
        assertThat(encryptionService.encrypt("   ")).isEqualTo("   ");

        assertThat(encryptionService.decrypt(null)).isNull();
        assertThat(encryptionService.decrypt("")).isEqualTo("");
        assertThat(encryptionService.decrypt("   ")).isEqualTo("   ");
    }

    @Test
    void should_produceSameCipher_when_samePlainText() {
        // Given
        String plain = "SamePlainText";

        // When
        String cipher1 = encryptionService.encrypt(plain);
        String cipher2 = encryptionService.encrypt(plain);

        // Then
        // AES ECB mode produces identical ciphertext for identical plaintext and key
        assertThat(cipher1).isEqualTo(cipher2);
    }
}

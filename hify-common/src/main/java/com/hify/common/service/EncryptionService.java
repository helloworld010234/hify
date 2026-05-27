package com.hify.common.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * 加密服务（AES ECB PKCS5Padding）
 * <p>
 * 用于敏感信息（如 API Key）的加密存储与解密。
 * <p>
 * <b>安全说明：</b>当前实现为简化版，生产环境建议使用：
 * <ul>
 *   <li>Jasypt Spring Boot Starter</li>
 *   <li>Spring Security Crypto（Encryptors.stronger）</li>
 *   <li>或外部 KMS（AWS KMS / Azure Key Vault）</li>
 * </ul>
 */
@Service
public class EncryptionService {

    private static final String ALGORITHM = "AES/ECB/PKCS5Padding";
    private static final String KEY_ALGORITHM = "AES";

    private final SecretKeySpec secretKey;

    public EncryptionService(@Value("${hify.encryption.key:HifyDefaultKey16}") String key) {
        // AES-128 要求密钥长度 16 字节
        String normalized = String.format("%-16s", key).substring(0, 16);
        this.secretKey = new SecretKeySpec(normalized.getBytes(StandardCharsets.UTF_8), KEY_ALGORITHM);
    }

    /**
     * 加密明文
     *
     * @param plainText 明文
     * @return Base64 密文，输入为 null/blank 时原样返回
     */
    public String encrypt(String plainText) {
        if (plainText == null || plainText.isBlank()) {
            return plainText;
        }
        try {
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey);
            byte[] encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(encrypted);
        } catch (Exception e) {
            throw new RuntimeException("加密失败", e);
        }
    }

    /**
     * 解密密文
     *
     * <p>若输入不是有效的 Base64 密文（例如数据库中仍为明文），则原样返回，
     * 避免在迁移期或测试环境因明文密钥导致运行时异常。</p>
     *
     * @param cipherText Base64 密文或明文
     * @return 明文，输入为 null/blank 时原样返回
     */
    public String decrypt(String cipherText) {
        if (cipherText == null || cipherText.isBlank()) {
            return cipherText;
        }
        try {
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, secretKey);
            byte[] decoded = Base64.getDecoder().decode(cipherText);
            return new String(cipher.doFinal(decoded), StandardCharsets.UTF_8);
        } catch (Exception e) {
            // 无法解密时视为明文，直接返回原值（兼容历史明文数据）
            return cipherText;
        }
    }
}

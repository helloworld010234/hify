package com.hify.common.util;

/**
 * Token 估算工具
 * <p>
 * 基于字符数和语言类型的简单估算：
 * <ul>
 *   <li>英文：1 token ≈ 4 个字符</li>
 *   <li>中文：1 个汉字 ≈ 1.5 tokens</li>
 * </ul>
 */
public class TokenUtil {

    /**
     * 估算文本的 token 数量
     *
     * @param text 输入文本
     * @return 估算的 token 数
     */
    public static int estimateTokens(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        int chinese = 0;
        int other = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c >= '\u4e00' && c <= '\u9fff') {
                chinese++;
            } else if (!Character.isWhitespace(c)) {
                other++;
            }
        }
        return (int) Math.round(chinese * 1.5 + other * 0.3);
    }
}

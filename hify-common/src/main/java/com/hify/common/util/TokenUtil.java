package com.hify.common.util;

/**
 * Token 估算工具类
 * <p>
 * 基于字符的简易估算策略（中文 * 1.5 + 其他 * 0.3），
 * 适用于滑动窗口预算计算、成本预估等场景。
 * <p>
 * 注意：此为近似值，与真实 tokenizer（如 cl100k_base）存在误差，
 * 仅用于上下文截断策略，不用于计费。
 */
public final class TokenUtil {

    private TokenUtil() {
        // utility class
    }

    /**
     * 估算文本对应的 token 数量
     *
     * @param text 文本内容
     * @return token 估算值（≥0）
     */
    public static int estimateTokens(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        int chinese = 0;
        int other = 0;
        for (char c : text.toCharArray()) {
            if (c >= '\u4e00' && c <= '\u9fff') {
                chinese++;
            } else {
                other++;
            }
        }
        return (int) Math.ceil(chinese * 1.5 + other * 0.3);
    }
}

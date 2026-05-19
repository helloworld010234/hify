package com.hify.modules.provider.domain.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 鉴权类型枚举
 * <p>
 * 统一抽象各供应商的鉴权差异：
 * <ul>
 *   <li>{@link #NONE} — Ollama 本地部署，无需鉴权或任意值</li>
 *   <li>{@link #BEARER} — OpenAI/DeepSeek/Kimi/Qwen/豆包/Gemini 等标准 Bearer Token</li>
 *   <li>{@link #API_KEY} — Anthropic 的 x-api-key 或其他自定义 Header 鉴权</li>
 *   <li>{@link #AZURE_API_KEY} — Azure OpenAI 的 api-key Header（非 Bearer）</li>
 * </ul>
 */
@Getter
@RequiredArgsConstructor
public enum AuthType {

    NONE("none", "无鉴权", null),
    BEARER("bearer", "Bearer Token", "Authorization"),
    API_KEY("api_key", "自定义 API Key Header", "x-api-key"),
    AZURE_API_KEY("azure_api_key", "Azure API Key Header", "api-key");

    private final String code;
    private final String description;

    /**
     * 请求头名称，null 表示无需请求头（如 NONE）
     */
    private final String headerName;

    public static AuthType fromCode(String code) {
        for (AuthType type : values()) {
            if (type.code.equalsIgnoreCase(code)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown auth type: " + code);
    }
}

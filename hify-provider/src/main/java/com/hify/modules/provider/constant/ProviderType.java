package com.hify.modules.provider.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 供应商协议类型
 * <p>
 * 决定 Hify 如何构造 HTTP 请求和解析响应。
 * 80% 的供应商走 OPENAI_COMPATIBLE，只需替换 baseUrl 和 apiKey。
 */
@Getter
@RequiredArgsConstructor
public enum ProviderType {

    /**
     * OpenAI 兼容格式（事实标准）
     * 适用：OpenAI、DeepSeek、Kimi、通义千问、豆包、Ollama、Gemini、智谱等
     */
    OPENAI_COMPATIBLE("openai_compatible", "/v1/chat/completions"),

    /**
     * Anthropic Messages API
     * 需特殊适配：system 字段独立、max_tokens 必填、content 为 block 数组
     */
    ANTHROPIC("anthropic", "/v1/messages"),

    /**
     * Azure OpenAI
     * 需特殊适配：URL 含 deployment，auth 为 api-key header，带 api-version query
     */
    AZURE_OPENAI("azure_openai", "/openai/deployments/{deployment}/chat/completions"),

    /**
     * Ollama 原生格式（备用，一般走 OPENAI_COMPATIBLE 即可）
     */
    OLLAMA("ollama", "/api/chat");

    private final String code;

    /**
     * 默认 Chat Endpoint 路径模板
     */
    private final String defaultChatEndpoint;

    public static ProviderType fromCode(String code) {
        for (ProviderType type : values()) {
            if (type.code.equalsIgnoreCase(code)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown provider type: " + code);
    }
}

package com.hify.modules.provider.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 鉴权额外配置值对象（存储于 t_provider.auth_config JSON 字段）
 * <p>
 * 设计意图：将各供应商的非敏感鉴权差异（如 api-version、deployment-name）
 * 统一收敛到一个可扩展的 POJO 中，避免为每个供应商建单独的字段。
 * <p>
 * 敏感信息（如 api-key）不放在此处，而是加密存储在 t_provider.api_key 字段。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthConfig implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Anthropic 专用：API 版本号，如 "2023-06-01"
     * Anthropic 要求在请求头中携带 anthropic-version
     */
    private String apiVersion;

    /**
     * Azure OpenAI 专用：Deployment 名称
     * Azure 的 URL 格式为 /deployments/{deployment}/chat/completions
     */
    private String deploymentName;

    /**
     * Azure OpenAI 专用：API 版本号，如 "2024-06-01"
     * 通过 query parameter api-version=xxx 传递
     */
    private String azureApiVersion;

    /**
     * 自定义请求头扩展（极少使用）
     * Key: 头名称，Value: 头值（非敏感）
     */
    // private Map<String, String> customHeaders;

    /**
     * 供应商特有的其他非敏感配置占位
     * 例如：Ollama 的 keep_alive 时长、某些厂商的 organization-id 等
     */
    private String extra;
}

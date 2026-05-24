package com.hify.modules.provider.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Provider 传输对象（供前端和其他模块使用）
 * <p>
 * 注意：此 DTO 不包含 apiKey 明文，仅展示掩码后的密钥信息。
 */
@Data
public class ProviderDto {

    private Long id;
    private String code;
    private String name;

    /**
     * 协议类型编码
     */
    private String providerType;

    private String baseUrl;

    /**
     * 鉴权类型编码
     */
    private String authType;

    /**
     * API 密钥掩码（如 sk-****abcd），仅用于前端提示已配置
     */
    private String apiKeyMask;

    /**
     * 鉴权额外配置（非敏感 JSON）
     */
    private Object authConfig;

    private Integer timeoutMs;
    private Integer maxRetries;

    /**
     * 人工状态
     */
    private String status;

    /**
     * 健康状态
     */
    private String healthStatus;

    private Integer consecutiveFailures;
    private LocalDateTime lastCheckTime;
    private String lastErrorMsg;

    /**
     * Fallback 供应商编码
     */
    private String fallbackProviderCode;

    private Integer sortOrder;
    private String remark;
    private LocalDateTime createdAt;

    /**
     * 该供应商下的模型列表（详情接口时填充）
     */
    private List<ModelDto> models;
}

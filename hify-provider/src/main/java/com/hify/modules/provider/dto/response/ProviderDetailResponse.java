package com.hify.modules.provider.dto.response;

import com.hify.modules.provider.dto.vo.AuthConfig;
import com.hify.modules.provider.entity.ModelConfig;
import com.hify.modules.provider.entity.ProviderHealth;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 供应商详情响应
 * <p>
 * 包含供应商基本信息、关联模型列表、健康状态快照。
 */
@Data
public class ProviderDetailResponse {

    private Long id;
    private String code;
    private String name;
    private String providerType;
    private String baseUrl;
    private String authType;

    /**
     * API 密钥掩码（如 sk-****abcd）
     */
    private String apiKeyMask;

    private AuthConfig authConfig;
    private Integer timeoutMs;
    private Integer maxRetries;
    private String status;
    private String healthStatus;
    private Integer consecutiveFailures;
    private LocalDateTime lastCheckTime;
    private String lastErrorMsg;
    private String fallbackProviderCode;
    private Integer sortOrder;
    private String remark;
    private LocalDateTime createdAt;

    /**
     * 关联的模型配置列表
     */
    private List<ModelConfig> modelConfigs;

    /**
     * 健康状态快照
     */
    private ProviderHealth providerHealth;
}

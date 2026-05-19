package com.hify.modules.provider.api.dto.request;

import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.hify.modules.provider.domain.vo.AuthConfig;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 创建供应商请求
 */
@Data
public class ProviderCreateRequest {

    private String code;

    @NotBlank(message = "供应商名称不能为空")
    private String name;

    private String providerType;

    @NotBlank(message = "Base URL 不能为空")
    private String baseUrl;

    private String authType;

    /**
     * API 密钥（明文，Service 层加密后存储）
     */
    private String apiKey;

    /**
     * 鉴权额外配置
     */
    private AuthConfig authConfig;

    public String getApiKey() {
        if (StringUtils.isNotBlank(apiKey)) {
            return apiKey;
        }
        if (authConfig != null && StringUtils.isNotBlank(authConfig.getApiKey())) {
            return authConfig.getApiKey();
        }
        return null;
    }

    private Integer timeoutMs = 90000;
    private Integer maxRetries = 3;

    @NotBlank(message = "状态不能为空")
    private String status = "active";

    private Long fallbackProviderId;
    private Integer sortOrder = 0;
    private String remark;
}

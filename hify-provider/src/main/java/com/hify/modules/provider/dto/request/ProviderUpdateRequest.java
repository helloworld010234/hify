package com.hify.modules.provider.dto.request;

import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.hify.modules.provider.dto.vo.AuthConfig;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 更新供应商请求
 */
@Data
public class ProviderUpdateRequest {

    @NotBlank(message = "供应商名称不能为空")
    private String name;

    @NotBlank(message = "Base URL 不能为空")
    private String baseUrl;

    private String authType;

    /**
     * API 密钥（明文，为空表示不修改原密钥）
     */
    private String apiKey;

    /**
     * 鉴权额外配置
     */
    private AuthConfig authConfig;

    private Integer timeoutMs = 90000;
    private Integer maxRetries = 3;

    @NotBlank(message = "状态不能为空")
    private String status;

    private Long fallbackProviderId;
    private Integer sortOrder = 0;
    private String remark;
}

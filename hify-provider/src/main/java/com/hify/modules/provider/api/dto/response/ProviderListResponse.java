package com.hify.modules.provider.api.dto.response;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 供应商列表项响应
 */
@Data
public class ProviderListResponse {

    private Long id;
    private String code;
    private String name;
    private String providerType;
    private String baseUrl;
    private String authType;
    private String status;
    private String healthStatus;
    private Integer consecutiveFailures;
    private LocalDateTime lastCheckTime;
    private Integer sortOrder;
    private LocalDateTime createdAt;
}

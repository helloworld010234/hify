package com.hify.modules.provider.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 供应商健康状态传输对象
 * <p>
 * 用于前端健康状态看板、熔断状态展示、运维告警。
 */
@Data
public class ProviderHealthDto {

    private Long providerId;
    private String providerCode;
    private String providerName;

    /**
     * 人工状态：active / inactive
     */
    private String status;

    /**
     * 自动健康状态：healthy / unhealthy / degraded / unknown
     */
    private String healthStatus;

    /**
     * 健康状态中文标签
     */
    private String healthStatusLabel;

    /**
     * 连续失败次数
     */
    private Integer consecutiveFailures;

    /**
     * 最近一次检查时间
     */
    private LocalDateTime lastCheckTime;

    /**
     * 最近一次错误信息
     */
    private String lastErrorMsg;

    /**
     * 是否已熔断（根据 consecutiveFailures >= 阈值判断）
     */
    private Boolean circuitOpen;

    /**
     * Fallback 供应商编码（熔断时的逃生通道）
     */
    private String fallbackProviderCode;

    /**
     * 响应延迟（最近一次健康检查的 RT，单位 ms）
     */
    private Long responseTimeMs;
}

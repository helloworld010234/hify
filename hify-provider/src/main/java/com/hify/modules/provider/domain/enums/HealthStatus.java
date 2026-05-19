package com.hify.modules.provider.domain.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 供应商健康状态（系统自动检测）
 * <p>
 * 与 {@link com.hify.modules.provider.domain.Provider#getStatus()} 人工状态分离，
 * 支持独立观测和熔断决策。
 */
@Getter
@RequiredArgsConstructor
public enum HealthStatus {

    /**
     * 未知：尚未执行过健康检查（新增供应商的初始状态）
     */
    UNKNOWN("unknown", "未检测"),

    /**
     * 健康：最近一次连通性测试通过
     */
    HEALTHY("healthy", "健康"),

    /**
     * 降级：可以连通但响应异常（如慢、限流、部分模型不可用）
     */
    DEGRADED("degraded", "降级"),

    /**
     * 不健康：连续多次连通失败，触发熔断或 Fallback
     */
    UNHEALTHY("unhealthy", "不健康");

    private final String code;
    private final String label;

    /**
     * 是否可用（HEALTHY 或 DEGRADED 视为可用，UNHEALTHY 不可用）
     */
    public boolean isAvailable() {
        return this == HEALTHY || this == DEGRADED;
    }

    public static HealthStatus fromCode(String code) {
        for (HealthStatus status : values()) {
            if (status.code.equalsIgnoreCase(code)) {
                return status;
            }
        }
        return UNKNOWN;
    }
}

package com.hify.modules.provider.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 供应商连通性测试结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConnectionTestResult {

    /**
     * 是否连通成功
     */
    private boolean success;

    /**
     * 响应延迟（毫秒）
     */
    private long latencyMs;

    /**
     * 可用模型数量（从 /v1/models 或 /api/tags 响应中解析）
     */
    private int modelCount;

    /**
     * 错误信息（success=false 时有值）
     */
    private String errorMessage;

    public static ConnectionTestResult success(long latencyMs, int modelCount) {
        return ConnectionTestResult.builder()
                .success(true)
                .latencyMs(latencyMs)
                .modelCount(modelCount)
                .build();
    }

    public static ConnectionTestResult fail(long latencyMs, String errorMessage) {
        return ConnectionTestResult.builder()
                .success(false)
                .latencyMs(latencyMs)
                .modelCount(0)
                .errorMessage(errorMessage)
                .build();
    }
}

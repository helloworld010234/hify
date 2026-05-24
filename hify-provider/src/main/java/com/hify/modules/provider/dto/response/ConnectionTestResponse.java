package com.hify.modules.provider.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 供应商连通性测试结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConnectionTestResponse {

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

    /**
     * 从远程拉取的模型列表（成功时填充）
     */
    private List<ModelInfo> models;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ModelInfo {
        private String modelCode;
        private String modelName;
    }

    public static ConnectionTestResponse success(long latencyMs, int modelCount, List<ModelInfo> models) {
        return ConnectionTestResponse.builder()
                .success(true)
                .latencyMs(latencyMs)
                .modelCount(modelCount)
                .models(models)
                .build();
    }

    public static ConnectionTestResponse fail(long latencyMs, String errorMessage) {
        return ConnectionTestResponse.builder()
                .success(false)
                .latencyMs(latencyMs)
                .modelCount(0)
                .errorMessage(errorMessage)
                .build();
    }
}

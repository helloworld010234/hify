package com.hify.modules.agent.api.dto.response;

import lombok.Data;

import java.util.List;

/**
 * 按供应商分组的模型列表
 */
@Data
public class ModelGroupResponse {

    private Long providerId;
    private String providerName;
    private List<ModelOption> models;

    @Data
    public static class ModelOption {
        private Long id;
        private String modelCode;
        private String modelName;
    }
}

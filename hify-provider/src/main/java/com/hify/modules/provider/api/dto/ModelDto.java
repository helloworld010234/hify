package com.hify.modules.provider.api.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * Model 传输对象
 */
@Data
public class ModelDto {

    private Long id;
    private Long providerId;

    /**
     * 供应商编码（冗余，便于前端分组展示）
     */
    private String providerCode;

    /**
     * 供应商名称（冗余）
     */
    private String providerName;

    private String modelCode;
    private String modelName;
    private String modelType;

    private Integer maxContextTokens;
    private Integer maxOutputTokens;

    /**
     * 能力开关
     */
    private Boolean supportsStreaming;
    private Boolean supportsToolCalls;
    private Boolean supportsVision;
    private Boolean supportsJsonMode;

    /**
     * 价格（元/百万token），前端展示用
     */
    private String inputPriceYuan;
    private String outputPriceYuan;

    private String status;
    private Boolean isDefault;
    private Integer sortOrder;
    private LocalDateTime createdAt;
}

package com.hify.modules.provider.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hify.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Model 持久化对象（对应 t_model 表）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_model")
public class ModelPo extends BaseEntity {

    private Long providerId;
    private String modelCode;
    private String modelName;

    /**
     * 模型类型编码：chat/embedding/vision/reasoning/multimodal
     */
    private String modelType;

    private Integer maxContextTokens;
    private Integer maxOutputTokens;
    private Integer supportsStreaming;
    private Integer supportsToolCalls;
    private Integer supportsVision;
    private Integer supportsJsonMode;

    private Long inputPrice;
    private Long outputPrice;

    /**
     * 模型状态：active / inactive / deprecated
     */
    private String status;

    private Integer isDefault;
    private Integer sortOrder;

}

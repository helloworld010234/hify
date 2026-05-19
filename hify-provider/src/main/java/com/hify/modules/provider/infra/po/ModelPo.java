package com.hify.modules.provider.infra.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Model 持久化对象（对应 t_model 表）
 */
@Data
@TableName("t_model")
public class ModelPo {

    @TableId(type = IdType.AUTO)
    private Long id;

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

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /**
     * 逻辑删除标志
     */
    private Integer deleted;
}

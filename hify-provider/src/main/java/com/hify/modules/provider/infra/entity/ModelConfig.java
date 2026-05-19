package com.hify.modules.provider.infra.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.hify.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Map;

/**
 * ModelConfig 实体（对应 t_model 表）
 * <p>
 * 继承 {@link BaseEntity}，统一包含 id、createdAt、updatedAt、deleted。
 * extra_params 字段通过 JacksonTypeHandler 实现 JSON 与 Map 的自动映射，
 * 用于存储模型特有的扩展参数（如 reasoning_effort、top_p、presence_penalty 等）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "t_model", autoResultMap = true)
public class ModelConfig extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /**
     * 所属供应商 ID
     */
    private Long providerId;

    /**
     * 模型唯一标识，如 gpt-4o / deepseek-chat / claude-sonnet-4
     */
    private String modelCode;

    /**
     * 显示名称
     */
    private String modelName;

    /**
     * 模型类型编码：chat / embedding / vision / reasoning / multimodal
     */
    private String modelType;

    /**
     * 最大上下文 Token 数
     */
    private Integer maxContextTokens;

    /**
     * 最大输出 Token 数
     */
    private Integer maxOutputTokens;

    /**
     * 是否支持流式输出（SSE）
     */
    private Integer supportsStreaming;

    /**
     * 是否支持工具调用（Function Calling）
     */
    private Integer supportsToolCalls;

    /**
     * 是否支持视觉输入
     */
    private Integer supportsVision;

    /**
     * 是否支持 JSON 模式 / 结构化输出
     */
    private Integer supportsJsonMode;

    /**
     * 输入价格（分/百万token）
     */
    private Long inputPrice;

    /**
     * 输出价格（分/百万token）
     */
    private Long outputPrice;

    /**
     * 扩展参数（JSON）
     * <p>存储模型特有的非结构化配置，如 OpenAI 的 reasoning_effort、
     * Anthropic 的 thinking 开关、Google 的 safety_settings 等。
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> extraParams;

    /**
     * 模型状态：active / inactive / deprecated
     */
    private String status;

    /**
     * 是否该供应商下的默认模型
     */
    private Integer isDefault;

    /**
     * 排序
     */
    private Integer sortOrder;
}

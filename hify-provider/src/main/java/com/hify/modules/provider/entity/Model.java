package com.hify.modules.provider.entity;

import com.hify.modules.provider.constant.ModelType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Model 领域对象（模型）
 * <p>
 * 一个 {@link Provider} 下可挂载多个 Model。
 * 模型独立管理自身状态、能力开关和价格，便于细粒度控制。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Model {

    private Long id;

    /**
     * 所属供应商 ID
     */
    private Long providerId;

    /**
     * 模型唯一标识（供应商内部唯一）
     * <p>例：gpt-4o、deepseek-chat、claude-sonnet-4-5、qwen-max
     */
    private String modelCode;

    /**
     * 显示名称
     * <p>例：GPT-4o、DeepSeek-V3、Claude Sonnet 4.5
     */
    private String modelName;

    /**
     * 模型类型
     */
    private ModelType modelType;

    // ==================== 能力参数 ====================

    /**
     * 最大上下文 Token 数（用于前端提示和长度校验）
     */
    private Integer maxContextTokens;

    /**
     * 最大输出 Token 数
     */
    private Integer maxOutputTokens;

    /**
     * 是否支持流式输出（SSE）
     */
    private Boolean supportsStreaming;

    /**
     * 是否支持工具调用（Function Calling / Tools）
     */
    private Boolean supportsToolCalls;

    /**
     * 是否支持视觉输入
     */
    private Boolean supportsVision;

    /**
     * 是否支持 JSON 模式 / 结构化输出
     */
    private Boolean supportsJsonMode;

    // ==================== 价格（单位：分/百万token）====================

    /**
     * 输入价格，null 表示按量不计费或免费
     */
    private Long inputPrice;

    /**
     * 输出价格
     */
    private Long outputPrice;

    // ==================== 状态 ====================

    /**
     * active / inactive / deprecated
     */
    private String status;

    /**
     * 是否该供应商下的默认模型
     * <p>新建 Agent 或对话时，默认选中此模型
     */
    private Boolean isDefault;

    private Integer sortOrder;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Boolean deleted;

    // ==================== 便捷方法 ====================

    /**
     * 是否可用
     */
    public boolean isActive() {
        return "active".equals(this.status);
    }

    /**
     * 计算预估费用（分）
     *
     * @param inputTokens  输入 token 数
     * @param outputTokens 输出 token 数
     * @return 预估费用（分）
     */
    public Long estimateCost(long inputTokens, long outputTokens) {
        long cost = 0L;
        if (inputPrice != null) {
            cost += inputTokens * inputPrice / 1_000_000L;
        }
        if (outputPrice != null) {
            cost += outputTokens * outputPrice / 1_000_000L;
        }
        return cost;
    }
}

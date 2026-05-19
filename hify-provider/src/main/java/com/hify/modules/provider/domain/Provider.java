package com.hify.modules.provider.domain;

import com.hify.modules.provider.domain.enums.AuthType;
import com.hify.modules.provider.domain.enums.HealthStatus;
import com.hify.modules.provider.domain.enums.ProviderType;
import com.hify.modules.provider.domain.vo.AuthConfig;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Provider 领域对象（供应商）
 * <p>
 * 核心职责：
 * 1. 封装 LLM 供应商的连接配置（URL、鉴权、超时）
 * 2. 承载健康状态与熔断信息
 * 3. 关联 fallback 供应商，实现故障自动转移
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Provider {

    private Long id;

    /**
     * 供应商唯一编码，如 "openai" / "deepseek" / "anthropic" / "azure_prod" / "ollama_local"
     * <p>code 具有业务唯一性，一个 code 对应一种供应商实例（允许同厂商多实例，如 azure_prod / azure_dev）
     */
    private String code;

    /**
     * 显示名称，如 "OpenAI" / "DeepSeek" / "阿里云通义千问"
     */
    private String name;

    /**
     * 协议类型，决定请求构造和响应解析方式
     */
    private ProviderType providerType;

    /**
     * API Base URL，末尾不带斜杠
     * 例：https://api.openai.com、http://localhost:11434
     */
    private String baseUrl;

    // ==================== 鉴权统一存储 ====================

    /**
     * 鉴权类型，决定如何构造请求头
     */
    private AuthType authType;

    /**
     * API 密钥（明文，仅在内存中使用，数据库中加密存储）
     * <p>落库前必须通过 EncryptionService 加密，出库后解密再赋值给领域对象
     */
    private String apiKey;

    /**
     * 鉴权额外配置（非敏感参数）
     * <p>如 Anthropic 的 apiVersion、Azure 的 deploymentName 等
     */
    private AuthConfig authConfig;

    // ==================== 连接参数 ====================

    /**
     * 请求超时（毫秒）
     */
    private Integer timeoutMs;

    /**
     * 最大重试次数
     */
    private Integer maxRetries;

    // ==================== 健康与熔断 ====================

    /**
     * 人工状态：active(启用) / inactive(禁用)
     * <p>由管理员手动控制，不受自动健康检查影响
     */
    private String status;

    /**
     * 自动健康状态（由系统定时检测）
     */
    private HealthStatus healthStatus;

    /**
     * 连续失败次数，达到阈值触发熔断
     */
    private Integer consecutiveFailures;

    /**
     * 最近一次健康检查时间
     */
    private LocalDateTime lastCheckTime;

    /**
     * 最近一次错误信息（用于前端展示和运维排查）
     */
    private String lastErrorMsg;

    /**
     * Fallback 供应商 ID（自关联）
     * <p>当本供应商熔断时，流量自动路由到 fallback 供应商
     */
    private Long fallbackProviderId;

    // ==================== 通用字段 ====================

    private Integer sortOrder;
    private String remark;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Boolean deleted;

    // ==================== 领域行为 ====================

    /**
     * 是否可参与路由（人工启用 + 健康状态允许）
     */
    public boolean isRoutable() {
        return "active".equals(this.status)
                && this.healthStatus != null
                && this.healthStatus.isAvailable();
    }

    /**
     * 是否已熔断（连续失败超过阈值）
     */
    public boolean isCircuitOpen(int threshold) {
        return this.consecutiveFailures != null
                && this.consecutiveFailures >= threshold;
    }

    /**
     * 记录一次健康检查成功
     */
    public void recordSuccess() {
        this.healthStatus = HealthStatus.HEALTHY;
        this.consecutiveFailures = 0;
        this.lastErrorMsg = null;
        this.lastCheckTime = LocalDateTime.now();
    }

    /**
     * 记录一次健康检查失败
     */
    public void recordFailure(String errorMsg) {
        this.healthStatus = HealthStatus.UNHEALTHY;
        this.consecutiveFailures = (this.consecutiveFailures == null ? 0 : this.consecutiveFailures) + 1;
        this.lastErrorMsg = errorMsg;
        this.lastCheckTime = LocalDateTime.now();
    }
}

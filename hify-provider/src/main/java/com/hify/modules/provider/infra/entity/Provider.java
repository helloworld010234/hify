package com.hify.modules.provider.infra.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.hify.common.entity.BaseEntity;
import com.hify.modules.provider.domain.vo.AuthConfig;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * Provider 实体（对应 t_provider 表）
 * <p>
 * 继承 {@link BaseEntity}，统一包含 id、createdAt、updatedAt、deleted。
 * auth_config 字段通过 JacksonTypeHandler 实现 JSON 与 POJO 的自动映射。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "t_provider", autoResultMap = true)
public class Provider extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /**
     * 供应商唯一编码，如 openai / deepseek / anthropic / azure_prod / ollama_local
     */
    private String code;

    /**
     * 显示名称
     */
    private String name;

    /**
     * 协议类型编码：openai_compatible / anthropic / azure_openai / ollama
     */
    private String providerType;

    /**
     * API Base URL
     */
    private String baseUrl;

    /**
     * 鉴权类型编码：none / bearer / api_key / azure_api_key
     */
    private String authType;

    /**
     * API 密钥密文（AES 加密存储）
     * <p>出库后需在 Service 层解密为明文使用
     */
    private String apiKey;

    /**
     * 鉴权额外配置（JSON），如 anthropic-version、azure deployment
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private AuthConfig authConfig;

    /**
     * 请求超时（毫秒）
     */
    private Integer timeoutMs;

    /**
     * 最大重试次数
     */
    private Integer maxRetries;

    /**
     * 人工状态：active / inactive
     */
    private String status;

    /**
     * 自动健康状态：healthy / unhealthy / degraded / unknown
     */
    private String healthStatus;

    /**
     * 连续失败次数（用于熔断判断）
     */
    private Integer consecutiveFailures;

    /**
     * 最近一次健康检查时间
     */
    private LocalDateTime lastCheckTime;

    /**
     * 最近一次错误信息
     */
    private String lastErrorMsg;

    /**
     * Fallback 供应商 ID
     */
    private Long fallbackProviderId;

    /**
     * 排序
     */
    private Integer sortOrder;

    /**
     * 备注
     */
    private String remark;
}

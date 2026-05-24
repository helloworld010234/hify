package com.hify.modules.provider.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.hify.common.entity.BaseEntity;
import com.hify.modules.provider.dto.vo.AuthConfig;

import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Provider 持久化对象（对应 t_provider 表）
 * <p>
 * 注意：api_key 字段落库前已加密，从库中取出的是密文字符串，
 * 需在 Service 层通过 EncryptionService 解密后再转换为领域对象。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "t_provider", autoResultMap = true)
public class ProviderPo extends BaseEntity {

    private String code;
    private String name;

    /**
     * 协议类型编码
     */
    private String providerType;

    private String baseUrl;

    /**
     * 鉴权类型编码
     */
    private String authType;

    /**
     * 加密后的 API 密钥密文
     */
    private String apiKey;

    /**
     * 鉴权额外配置，JSON 存储，通过 Jackson 自动序列化/反序列化
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private AuthConfig authConfig;

    private Integer timeoutMs;
    private Integer maxRetries;

    /**
     * 人工状态：active / inactive
     */
    private String status;

    /**
     * 健康状态：healthy / unhealthy / degraded / unknown
     */
    private String healthStatus;

    private Integer consecutiveFailures;
    private LocalDateTime lastCheckTime;
    private String lastErrorMsg;
    private Long fallbackProviderId;

    private Integer sortOrder;
    private String remark;
}

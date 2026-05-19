package com.hify.modules.provider.infra.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * ProviderHealth 实体（对应 t_provider_health 表）
 * <p>
 * <b>不继承 {@link com.hify.common.entity.BaseEntity}</b>，拥有独立的字段结构：
 * <ul>
 *   <li>无 createdAt / updatedAt（健康快照不需要审计时间戳）</li>
 *   <li>无 deleted（健康记录不使用逻辑删除，物理清理过期数据即可）</li>
 * </ul>
 * <p>
 * 每张表仅保留每个供应商的最新一条健康快照，通过 {@code provider_id} 唯一索引约束。
 */
@Data
@TableName("t_provider_health")
public class ProviderHealth implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 关联供应商 ID（t_provider.id）
     */
    private Long providerId;

    /**
     * 健康状态：healthy / unhealthy / degraded / unknown
     */
    private String healthStatus;

    /**
     * 连续失败次数（达到阈值触发熔断）
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
     * 最近一次响应耗时（毫秒）
     */
    private Long responseTimeMs;
}

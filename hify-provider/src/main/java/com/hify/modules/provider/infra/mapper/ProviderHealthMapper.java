package com.hify.modules.provider.infra.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hify.modules.provider.infra.entity.ProviderHealth;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * ProviderHealth Mapper
 * <p>
 * 继承 {@link BaseMapper}，获得 MyBatis-Plus 标准 CRUD 能力。
 * <p>
 * 由于 {@link ProviderHealth} 不继承 BaseEntity，对应的表 t_provider_health
 * 无 deleted 字段，因此查询时无需考虑逻辑删除过滤。
 */
@Mapper
public interface ProviderHealthMapper extends BaseMapper<ProviderHealth> {

    /**
     * 根据供应商 ID 查询健康快照
     */
    @Select("SELECT * FROM t_provider_health WHERE provider_id = #{providerId} LIMIT 1")
    ProviderHealth selectByProviderId(@Param("providerId") Long providerId);

    /**
     * 更新指定供应商的健康快照（不存在则插入，由调用方保证）
     */
    @Update("""
        UPDATE t_provider_health
        SET health_status = #{healthStatus},
            consecutive_failures = #{consecutiveFailures},
            last_check_time = #{lastCheckTime},
            last_error_msg = #{lastErrorMsg},
            response_time_ms = #{responseTimeMs}
        WHERE provider_id = #{providerId}
        """)
    int updateByProviderId(@Param("providerId") Long providerId,
                           @Param("healthStatus") String healthStatus,
                           @Param("consecutiveFailures") Integer consecutiveFailures,
                           @Param("lastCheckTime") java.time.LocalDateTime lastCheckTime,
                           @Param("lastErrorMsg") String lastErrorMsg,
                           @Param("responseTimeMs") Long responseTimeMs);
}

package com.hify.modules.provider.infra.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hify.modules.provider.infra.po.ProviderPo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * Provider Mapper
 */
@Mapper
public interface ProviderMapper extends BaseMapper<ProviderPo> {

    /**
     * 查询所有可用供应商（人工 active + 逻辑未删除）
     */
    @Select("SELECT * FROM t_provider WHERE deleted = 0 AND status = 'active' ORDER BY sort_order, id")
    List<ProviderPo> selectAllActive();

    /**
     * 按编码查询（排除已删除）
     */
    @Select("SELECT * FROM t_provider WHERE deleted = 0 AND code = #{code} LIMIT 1")
    ProviderPo selectByCode(@Param("code") String code);

    /**
     * 更新健康状态（乐观锁无关的局部更新）
     */
    @Update("""
        UPDATE t_provider
        SET health_status = #{healthStatus},
            consecutive_failures = #{consecutiveFailures},
            last_error_msg = #{lastErrorMsg},
            last_check_time = NOW(),
            updated_at = NOW()
        WHERE id = #{id} AND deleted = 0
        """)
    int updateHealth(@Param("id") Long id,
                     @Param("healthStatus") String healthStatus,
                     @Param("consecutiveFailures") Integer consecutiveFailures,
                     @Param("lastErrorMsg") String lastErrorMsg);
}

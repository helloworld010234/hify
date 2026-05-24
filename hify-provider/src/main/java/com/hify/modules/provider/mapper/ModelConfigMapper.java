package com.hify.modules.provider.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hify.modules.provider.entity.ModelConfig;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * ModelConfig Mapper
 * <p>
 * 继承 {@link BaseMapper}，获得 MyBatis-Plus 标准 CRUD 能力。
 */
@Mapper
public interface ModelConfigMapper extends BaseMapper<ModelConfig> {

    /**
     * 查询指定供应商下所有可用模型
     */
    @Select("SELECT * FROM t_model WHERE deleted = 0 AND provider_id = #{providerId} AND status = 'active' ORDER BY sort_order, id")
    List<ModelConfig> selectActiveByProviderId(@Param("providerId") Long providerId);

    /**
     * 查询指定供应商下的默认模型
     */
    @Select("SELECT * FROM t_model WHERE deleted = 0 AND provider_id = #{providerId} AND is_default = 1 LIMIT 1")
    ModelConfig selectDefaultByProviderId(@Param("providerId") Long providerId);

    /**
     * 查询所有可用模型（关联供应商，用于前端下拉选择）
     */
    @Select("""
        SELECT m.* FROM t_model m
        INNER JOIN t_provider p ON m.provider_id = p.id
        WHERE m.deleted = 0 AND p.deleted = 0
          AND m.status = 'active' AND p.status = 'active'
        ORDER BY p.sort_order, m.sort_order
        """)
    List<ModelConfig> selectAllActive();
}

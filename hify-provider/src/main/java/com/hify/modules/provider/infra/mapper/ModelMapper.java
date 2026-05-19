package com.hify.modules.provider.infra.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hify.modules.provider.infra.po.ModelPo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * Model Mapper
 */
@Mapper
public interface ModelMapper extends BaseMapper<ModelPo> {

    /**
     * 查询指定供应商下所有可用模型
     */
    @Select("SELECT * FROM t_model WHERE deleted = 0 AND provider_id = #{providerId} AND status = 'active' ORDER BY sort_order, id")
    List<ModelPo> selectActiveByProviderId(@Param("providerId") Long providerId);

    /**
     * 查询指定供应商下的默认模型
     */
    @Select("SELECT * FROM t_model WHERE deleted = 0 AND provider_id = #{providerId} AND is_default = 1 LIMIT 1")
    ModelPo selectDefaultByProviderId(@Param("providerId") Long providerId);

    /**
     * 查询所有可用模型（带供应商信息，用于前端下拉选择）
     */
    @Select("""
        SELECT m.* FROM t_model m
        INNER JOIN t_provider p ON m.provider_id = p.id
        WHERE m.deleted = 0 AND p.deleted = 0
          AND m.status = 'active' AND p.status = 'active'
        ORDER BY p.sort_order, m.sort_order
        """)
    List<ModelPo> selectAllActiveWithProvider();
}

package com.hify.modules.provider.infra.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hify.modules.provider.infra.entity.Provider;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * Provider Mapper
 * <p>
 * 继承 {@link BaseMapper}，获得 MyBatis-Plus 标准 CRUD 能力。
 */
@Mapper
public interface ProviderMapper extends BaseMapper<Provider> {

    /**
     * 查询所有人工启用的供应商（用于路由选择）
     */
    @Select("SELECT * FROM t_provider WHERE deleted = 0 AND status = 'active' ORDER BY sort_order, id")
    List<Provider> selectActiveList();

    /**
     * 根据编码查询（排除已删除）
     */
    @Select("SELECT * FROM t_provider WHERE deleted = 0 AND code = #{code} LIMIT 1")
    Provider selectByCode(@Param("code") String code);

    /**
     * 查询指定供应商的 Fallback 配置
     */
    @Select("""
        SELECT p.* FROM t_provider p
        INNER JOIN t_provider self ON self.fallback_provider_id = p.id
        WHERE self.deleted = 0 AND p.deleted = 0 AND self.code = #{code}
        LIMIT 1
        """)
    Provider selectFallbackByCode(@Param("code") String code);
}

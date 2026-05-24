package com.hify.modules.knowledge.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hify.modules.knowledge.entity.KnowledgeBase;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 知识库 Mapper
 */
@Mapper
public interface KnowledgeBaseMapper extends BaseMapper<KnowledgeBase> {

    /**
     * 根据名称查询（排除已删除）
     */
    @Select("SELECT * FROM t_knowledge_base WHERE deleted = 0 AND name = #{name} LIMIT 1")
    KnowledgeBase selectByName(@Param("name") String name);
}

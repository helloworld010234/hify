package com.hify.modules.knowledge.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hify.modules.knowledge.entity.Document;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 文档 Mapper
 */
@Mapper
public interface DocumentMapper extends BaseMapper<Document> {

    /**
     * 按知识库 ID 逻辑删除所有关联文档
     */
    @Update("UPDATE t_document SET deleted = 1 WHERE knowledge_base_id = #{kbId} AND deleted = 0")
    int deleteByKnowledgeBaseId(@Param("kbId") Long kbId);
}

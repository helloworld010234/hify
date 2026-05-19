package com.hify.modules.agent.infra.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hify.modules.agent.infra.entity.AgentKnowledgeRel;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * Agent-知识库关联 Mapper
 */
@Mapper
public interface AgentKnowledgeRelMapper extends BaseMapper<AgentKnowledgeRel> {

    /**
     * 根据 Agent ID 查询关联的知识库 ID 列表
     */
    @Select("SELECT knowledge_id FROM t_agent_knowledge_rel WHERE agent_id = #{agentId}")
    List<Long> selectKnowledgeIdsByAgentId(@Param("agentId") Long agentId);

    /**
     * 根据 Agent ID 删除所有关联
     */
    @Select("DELETE FROM t_agent_knowledge_rel WHERE agent_id = #{agentId}")
    void deleteByAgentId(@Param("agentId") Long agentId);
}

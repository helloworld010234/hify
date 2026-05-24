package com.hify.modules.agent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hify.modules.agent.entity.AgentKnowledgeRel;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

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

    /**
     * 批量统计各 Agent 的知识库数量
     */
    @Select("<script>SELECT agent_id as `agentId`, COUNT(*) as `cnt` FROM t_agent_knowledge_rel WHERE agent_id IN <foreach collection='agentIds' item='id' open='(' separator=',' close=')'>#{id}</foreach> GROUP BY agent_id</script>")
    List<Map<String, Object>> countByAgentIds(@Param("agentIds") List<Long> agentIds);

    /**
     * 批量插入 Agent-知识库关联
     */
    @Insert("<script>INSERT INTO t_agent_knowledge_rel (agent_id, knowledge_id) VALUES " +
            "<foreach collection='knowledgeIds' item='knowledgeId' separator=','>" +
            "(#{agentId}, #{knowledgeId})" +
            "</foreach></script>")
    void batchInsert(@Param("agentId") Long agentId, @Param("knowledgeIds") List<Long> knowledgeIds);
}

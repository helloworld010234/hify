package com.hify.modules.agent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hify.modules.agent.entity.AgentToolRel;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * Agent-工具关联 Mapper
 */
@Mapper
public interface AgentToolRelMapper extends BaseMapper<AgentToolRel> {

    /**
     * 根据 Agent ID 查询关联的工具 ID 列表
     */
    @Select("SELECT tool_id FROM t_agent_tool WHERE agent_id = #{agentId}")
    List<Long> selectToolIdsByAgentId(@Param("agentId") Long agentId);

    /**
     * 根据 Agent ID 删除所有关联
     */
    @Select("DELETE FROM t_agent_tool WHERE agent_id = #{agentId}")
    void deleteByAgentId(@Param("agentId") Long agentId);

    /**
     * 批量统计各 Agent 的工具数量
     */
    @Select("<script>SELECT agent_id as `agentId`, COUNT(*) as `cnt` FROM t_agent_tool WHERE agent_id IN <foreach collection='agentIds' item='id' open='(' separator=',' close=')'>#{id}</foreach> GROUP BY agent_id</script>")
    List<Map<String, Object>> countByAgentIds(@Param("agentIds") List<Long> agentIds);

    /**
     * 批量查询各 Agent 的工具 ID 列表
     */
    @Select("<script>SELECT agent_id as `agentId`, tool_id as `toolId` FROM t_agent_tool WHERE agent_id IN <foreach collection='agentIds' item='id' open='(' separator=',' close=')'>#{id}</foreach></script>")
    List<Map<String, Object>> selectToolIdsByAgentIds(@Param("agentIds") List<Long> agentIds);

    /**
     * 批量插入 Agent-工具关联
     */
    @Insert("<script>INSERT INTO t_agent_tool (agent_id, tool_id) VALUES " +
            "<foreach collection='toolIds' item='toolId' separator=','>" +
            "(#{agentId}, #{toolId})" +
            "</foreach></script>")
    void batchInsert(@Param("agentId") Long agentId, @Param("toolIds") List<Long> toolIds);
}

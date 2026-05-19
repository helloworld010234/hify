package com.hify.modules.agent.infra.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hify.modules.agent.infra.entity.AgentToolRel;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

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
}

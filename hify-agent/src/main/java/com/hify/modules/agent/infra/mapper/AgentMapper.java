package com.hify.modules.agent.infra.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hify.modules.agent.infra.entity.Agent;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * Agent Mapper
 */
@Mapper
public interface AgentMapper extends BaseMapper<Agent> {

    /**
     * 查询所有启用的 Agent
     */
    @Select("SELECT * FROM t_agent WHERE deleted = 0 AND enabled = 1 ORDER BY id")
    List<Agent> selectEnabledList();

    /**
     * 根据名称查询（排除已删除）
     */
    @Select("SELECT * FROM t_agent WHERE deleted = 0 AND name = #{name} LIMIT 1")
    Agent selectByName(@Param("name") String name);
}

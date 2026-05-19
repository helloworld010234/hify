package com.hify.modules.agent.infra.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hify.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Agent-MCP 工具关联实体（对应 t_agent_tool 表）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_agent_tool")
public class AgentToolRel extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /**
     * Agent ID（t_agent.id）
     */
    private Long agentId;

    /**
     * 工具 ID（mcp_server.id）
     */
    private Long toolId;
}

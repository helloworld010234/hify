package com.hify.modules.mcp.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;

import com.hify.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * MCP Tool 实体（对应 t_mcp_tool 表）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "t_mcp_tool", autoResultMap = true)
public class McpTool extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /**
     * 所属 MCP Server ID
     */
    private Long serverId;

    /**
     * 工具名称
     */
    private String name;

    /**
     * 工具描述
     */
    private String description;

    /**
     * 输入参数 Schema（JSON）
     */
    @TableField("input_schema")
    private String inputSchema;
}

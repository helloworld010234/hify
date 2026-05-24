package com.hify.modules.mcp.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hify.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * MCP Server 实体（对应 t_mcp_server 表）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_mcp_server")
public class McpServer extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /**
     * Server 显示名称
     */
    private String name;

    /**
     * MCP Server 端点地址
     */
    private String endpoint;

    /**
     * 是否启用：1-启用 0-禁用
     */
    private Integer enabled;

    /**
     * 连通状态：connected / disconnected / unknown
     */
    private String status;

    /**
     * 工具数量
     */
    private Integer toolCount;

    /**
     * 最近一次连通测试时间
     */
    private java.time.LocalDateTime lastCheckTime;

    /**
     * 最近一次错误信息
     */
    private String lastErrorMsg;
}

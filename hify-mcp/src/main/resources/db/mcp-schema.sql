-- ============================================
-- Hify MCP 模块数据库设计
-- ============================================

-- ------------------------------------------
-- t_mcp_server：MCP Server 配置表
-- ------------------------------------------
CREATE TABLE IF NOT EXISTS t_mcp_server (
    id              BIGINT          NOT NULL AUTO_INCREMENT COMMENT '主键',
    name            VARCHAR(64)     NOT NULL COMMENT 'Server 显示名称',
    endpoint        VARCHAR(255)    NOT NULL COMMENT 'MCP Server 端点地址',
    enabled         TINYINT(1)      NOT NULL DEFAULT 1 COMMENT '是否启用：1-启用 0-禁用',
    status          VARCHAR(16)     NOT NULL DEFAULT 'unknown' COMMENT '连通状态：connected/disconnected/unknown',
    tool_count      INT             NOT NULL DEFAULT 0 COMMENT '工具数量',
    last_check_time DATETIME(3)     COMMENT '最近一次连通测试时间',
    last_error_msg  VARCHAR(512)    COMMENT '最近一次错误信息',

    created_at      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    deleted         TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '逻辑删除标志',

    PRIMARY KEY (id),
    UNIQUE KEY uk_name_deleted (name, deleted),
    KEY idx_status_deleted (status, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='MCP Server 配置表';

-- ------------------------------------------
-- t_mcp_tool：MCP 工具配置表
-- ------------------------------------------
CREATE TABLE IF NOT EXISTS t_mcp_tool (
    id              BIGINT          NOT NULL AUTO_INCREMENT COMMENT '主键',
    server_id       BIGINT          NOT NULL COMMENT '所属 MCP Server ID',
    name            VARCHAR(64)     NOT NULL COMMENT '工具名称',
    description     VARCHAR(500)    COMMENT '工具描述',
    input_schema    JSON            COMMENT '输入参数 Schema（JSON）',

    created_at      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    deleted         TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '逻辑删除标志',

    PRIMARY KEY (id),
    UNIQUE KEY uk_server_tool_deleted (server_id, name, deleted),
    KEY idx_server_id (server_id, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='MCP 工具配置表';

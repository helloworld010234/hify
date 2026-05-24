-- ============================================
-- Hify Agent 模块数据库设计
-- 设计原则：
-- 1. Agent 配置精简平铺，核心参数直接列化
-- 2. 通过关联表绑定工具（MCP）和知识库
-- 3. 命名统一加 t_ 前缀
-- ============================================

-- ------------------------------------------
-- t_agent：AI Agent 配置主表
-- ------------------------------------------
CREATE TABLE IF NOT EXISTS t_agent (
    id                  BIGINT          NOT NULL AUTO_INCREMENT COMMENT '主键',
    name                VARCHAR(100)    NOT NULL COMMENT 'Agent 名称',
    description         VARCHAR(500)    NOT NULL DEFAULT '' COMMENT 'Agent 描述',
    system_prompt       TEXT            COMMENT '角色指令，可以很长',

    -- 模型绑定（直接关联 t_model.id）
    model_config_id     BIGINT          NOT NULL COMMENT '绑定的模型配置（t_model.id）',

    -- 核心参数
    temperature         DECIMAL(3,2)    NOT NULL DEFAULT 0.70 COMMENT '温度（0.00 ~ 1.00）',
    max_tokens          INT             NOT NULL DEFAULT 2048 COMMENT '最大输出 token 数',
    max_context_turns   INT             NOT NULL DEFAULT 10 COMMENT '保留最近几轮上下文',

    -- 状态
    enabled             TINYINT(1)      NOT NULL DEFAULT 1 COMMENT '是否启用：1-启用 0-禁用',

    created_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    deleted             TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '逻辑删除标志',

    PRIMARY KEY (id),
    UNIQUE KEY uk_name_deleted (name, deleted),
    KEY idx_model_config (model_config_id, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Agent 配置表';

-- ------------------------------------------
-- t_agent_tool：Agent 与 MCP 工具关联表
-- ------------------------------------------
CREATE TABLE IF NOT EXISTS t_agent_tool (
    id            BIGINT          NOT NULL AUTO_INCREMENT COMMENT '主键',
    agent_id      BIGINT          NOT NULL COMMENT 'Agent ID（t_agent.id）',
    tool_id       BIGINT          NOT NULL COMMENT '关联工具 ID（t_mcp_tool.id）',
    created_at    DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),

    PRIMARY KEY (id),
    UNIQUE KEY uk_agent_tool (agent_id, tool_id),
    KEY idx_agent_id (agent_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Agent 与工具关联表';

-- ------------------------------------------
-- t_agent_knowledge_rel：Agent-知识库关联表（预留）
-- ------------------------------------------
CREATE TABLE IF NOT EXISTS t_agent_knowledge_rel (
    id            BIGINT          NOT NULL AUTO_INCREMENT COMMENT '主键',
    agent_id      BIGINT          NOT NULL COMMENT 'Agent ID（t_agent.id）',
    knowledge_id  BIGINT          NOT NULL COMMENT '知识库 ID（t_knowledge.id）',
    created_at    DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),

    PRIMARY KEY (id),
    UNIQUE KEY uk_agent_knowledge (agent_id, knowledge_id),
    KEY idx_agent_id (agent_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Agent-知识库关联表';

-- ------------------------------------------
-- 扩展：Agent 表增加 knowledge_base_id 字段（单知识库绑定）
-- ------------------------------------------
ALTER TABLE t_agent
    ADD COLUMN knowledge_base_id BIGINT DEFAULT NULL COMMENT '绑定的知识库 ID（t_knowledge_base.id）' AFTER model_config_id,
    ADD KEY idx_knowledge_base (knowledge_base_id, deleted);

-- ------------------------------------------
-- 扩展：Agent 表增加 workflow_id 字段（绑定工作流）
-- ------------------------------------------
ALTER TABLE t_agent
    ADD COLUMN workflow_id BIGINT DEFAULT NULL COMMENT '绑定的工作流 ID（t_workflow.id）' AFTER knowledge_base_id,
    ADD KEY idx_workflow (workflow_id, deleted);

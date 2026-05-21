-- ============================================
-- Hify Agent 模块数据库设计（H2 兼容版本）
-- ============================================

CREATE TABLE IF NOT EXISTS t_agent (
    id                  BIGINT          NOT NULL AUTO_INCREMENT PRIMARY KEY,
    name                VARCHAR(100)    NOT NULL,
    description         VARCHAR(500)    NOT NULL DEFAULT '',
    system_prompt       CLOB,
    model_config_id     BIGINT          NOT NULL,
    knowledge_base_id   BIGINT,
    temperature         DECIMAL(3,2)    NOT NULL DEFAULT 0.70,
    max_tokens          INT             NOT NULL DEFAULT 2048,
    max_context_turns   INT             NOT NULL DEFAULT 10,
    enabled             BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted             BOOLEAN         NOT NULL DEFAULT FALSE
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_name_deleted ON t_agent(name, deleted);
CREATE INDEX IF NOT EXISTS idx_model_config ON t_agent(model_config_id, deleted);
CREATE INDEX IF NOT EXISTS idx_knowledge_base ON t_agent(knowledge_base_id, deleted);

CREATE TABLE IF NOT EXISTS t_agent_tool (
    id            BIGINT          NOT NULL AUTO_INCREMENT PRIMARY KEY,
    agent_id      BIGINT          NOT NULL,
    tool_id       BIGINT          NOT NULL,
    created_at    TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_agent_tool ON t_agent_tool(agent_id, tool_id);
CREATE INDEX IF NOT EXISTS idx_agent_tool_agent_id ON t_agent_tool(agent_id);

CREATE TABLE IF NOT EXISTS t_agent_knowledge_rel (
    id            BIGINT          NOT NULL AUTO_INCREMENT PRIMARY KEY,
    agent_id      BIGINT          NOT NULL,
    knowledge_id  BIGINT          NOT NULL,
    created_at    TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_agent_knowledge ON t_agent_knowledge_rel(agent_id, knowledge_id);
CREATE INDEX IF NOT EXISTS idx_agent_knowledge_agent_id ON t_agent_knowledge_rel(agent_id);

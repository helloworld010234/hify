-- ============================================
-- Hify Workflow 模块数据库设计（H2 兼容版本）
-- ============================================

CREATE TABLE IF NOT EXISTS t_workflow (
    id                  BIGINT          NOT NULL AUTO_INCREMENT PRIMARY KEY,
    name                VARCHAR(100)    NOT NULL,
    description         VARCHAR(500)    NOT NULL DEFAULT '',
    enabled             BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted             BOOLEAN         NOT NULL DEFAULT FALSE
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_name_deleted ON t_workflow(name, deleted);
CREATE INDEX IF NOT EXISTS idx_enabled_deleted ON t_workflow(enabled, deleted);

CREATE TABLE IF NOT EXISTS t_workflow_node (
    id                  BIGINT          NOT NULL AUTO_INCREMENT PRIMARY KEY,
    workflow_id         BIGINT          NOT NULL,
    node_key            VARCHAR(50)     NOT NULL,
    node_type           VARCHAR(20)     NOT NULL,
    name                VARCHAR(100)    NOT NULL DEFAULT '',
    config_json         CLOB            NOT NULL,
    created_at          TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted             BOOLEAN         NOT NULL DEFAULT FALSE
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_workflow_node_key ON t_workflow_node(workflow_id, node_key, deleted);
CREATE INDEX IF NOT EXISTS idx_workflow_node_workflow_id ON t_workflow_node(workflow_id, deleted);

CREATE TABLE IF NOT EXISTS t_workflow_edge (
    id                  BIGINT          NOT NULL AUTO_INCREMENT PRIMARY KEY,
    workflow_id         BIGINT          NOT NULL,
    source_node_key     VARCHAR(50)     NOT NULL,
    target_node_key     VARCHAR(50)     NOT NULL,
    condition_expr      VARCHAR(500)    NOT NULL DEFAULT '',
    sort_order          INT             NOT NULL DEFAULT 0,
    created_at          TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted             BOOLEAN         NOT NULL DEFAULT FALSE
);

CREATE INDEX IF NOT EXISTS idx_workflow_edge_workflow_id ON t_workflow_edge(workflow_id, deleted);
CREATE INDEX IF NOT EXISTS idx_workflow_edge_source_node ON t_workflow_edge(workflow_id, source_node_key, deleted);

CREATE TABLE IF NOT EXISTS t_workflow_run (
    id           BIGINT          NOT NULL AUTO_INCREMENT PRIMARY KEY,
    workflow_id  BIGINT          NOT NULL,
    status       VARCHAR(20)     NOT NULL,
    input        CLOB,
    output       CLOB,
    error        VARCHAR(500)    NOT NULL DEFAULT '',
    elapsed_ms   INT,
    created_at   TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    finished_at  TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_workflow_run_workflow_id ON t_workflow_run(workflow_id);

CREATE TABLE IF NOT EXISTS t_workflow_node_run (
    id              BIGINT          NOT NULL AUTO_INCREMENT PRIMARY KEY,
    workflow_run_id BIGINT          NOT NULL,
    node_key        VARCHAR(64)     NOT NULL,
    node_type       VARCHAR(30)     NOT NULL,
    status          VARCHAR(20)     NOT NULL,
    outputs         CLOB,
    error           VARCHAR(500)    NOT NULL DEFAULT '',
    elapsed_ms      INT,
    created_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    finished_at     TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_workflow_node_run_run_id ON t_workflow_node_run(workflow_run_id);

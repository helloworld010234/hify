-- ============================================
-- Hify Workflow 模块数据库设计
-- ============================================

-- ------------------------------------------
-- t_workflow：工作流定义主表
-- ------------------------------------------
CREATE TABLE IF NOT EXISTS t_workflow (
    id                  BIGINT          NOT NULL AUTO_INCREMENT COMMENT '主键',
    name                VARCHAR(100)    NOT NULL COMMENT '工作流名称',
    description         VARCHAR(500)    NOT NULL DEFAULT '' COMMENT '工作流描述',
    enabled             TINYINT(1)      NOT NULL DEFAULT 1 COMMENT '是否启用：1-启用 0-禁用',

    created_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    deleted             TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '逻辑删除标志',

    PRIMARY KEY (id),
    UNIQUE KEY uk_name_deleted (name, deleted),
    KEY idx_enabled_deleted (enabled, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='工作流定义表';

-- ------------------------------------------
-- t_workflow_node：工作流节点定义表
-- ------------------------------------------
CREATE TABLE IF NOT EXISTS t_workflow_node (
    id                  BIGINT          NOT NULL AUTO_INCREMENT COMMENT '主键',
    workflow_id         BIGINT          NOT NULL COMMENT '所属工作流 ID（t_workflow.id）',
    node_key            VARCHAR(50)     NOT NULL COMMENT '节点业务标识，如 "intent-judge"',
    node_type           VARCHAR(20)     NOT NULL COMMENT '节点类型：START/LLM/CONDITION/API_CALL/KNOWLEDGE/END',
    name                VARCHAR(100)    NOT NULL DEFAULT '' COMMENT '节点展示名称',
    config_json         TEXT            NOT NULL COMMENT '节点配置 JSON',

    created_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    deleted             TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '逻辑删除标志',

    PRIMARY KEY (id),
    UNIQUE KEY uk_workflow_node_key (workflow_id, node_key, deleted),
    KEY idx_workflow_id (workflow_id, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='工作流节点定义表';

-- ------------------------------------------
-- t_workflow_edge：工作流节点连接关系表
-- ------------------------------------------
CREATE TABLE IF NOT EXISTS t_workflow_edge (
    id                  BIGINT          NOT NULL AUTO_INCREMENT COMMENT '主键',
    workflow_id         BIGINT          NOT NULL COMMENT '所属工作流 ID（t_workflow.id）',
    source_node_key     VARCHAR(50)     NOT NULL COMMENT '源节点标识',
    target_node_key     VARCHAR(50)     NOT NULL COMMENT '目标节点标识',
    condition_expr      VARCHAR(500)    NOT NULL DEFAULT '' COMMENT '条件表达式（条件分支节点出边使用）',
    sort_order          INT             NOT NULL DEFAULT 0 COMMENT '同 source 的多条边排序',

    created_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    deleted             TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '逻辑删除标志',

    PRIMARY KEY (id),
    KEY idx_workflow_id (workflow_id, deleted),
    KEY idx_source_node (workflow_id, source_node_key, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='工作流节点连接关系表';

-- ------------------------------------------
-- t_workflow_run：工作流执行记录
-- ------------------------------------------
CREATE TABLE IF NOT EXISTS t_workflow_run (
    id           BIGINT          NOT NULL AUTO_INCREMENT COMMENT '主键',
    workflow_id  BIGINT          NOT NULL COMMENT '所属工作流 ID',
    status       VARCHAR(20)     NOT NULL COMMENT '执行状态：RUNNING / SUCCESS / FAILED',
    input        TEXT            COMMENT '输入参数 JSON',
    output       TEXT            COMMENT '输出结果',
    error        VARCHAR(500)    NOT NULL DEFAULT '' COMMENT '错误信息',
    elapsed_ms   INT             COMMENT '耗时（毫秒）',
    created_at   DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    finished_at  DATETIME(3)     COMMENT '完成时间',

    PRIMARY KEY (id),
    KEY idx_workflow_id (workflow_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='工作流执行记录';

-- ------------------------------------------
-- t_workflow_node_run：节点执行记录
-- ------------------------------------------
CREATE TABLE IF NOT EXISTS t_workflow_node_run (
    id              BIGINT          NOT NULL AUTO_INCREMENT COMMENT '主键',
    workflow_run_id BIGINT          NOT NULL COMMENT '执行记录 ID（t_workflow_run.id）',
    node_key        VARCHAR(64)     NOT NULL COMMENT '节点标识',
    node_type       VARCHAR(30)     NOT NULL COMMENT '节点类型',
    status          VARCHAR(20)     NOT NULL COMMENT '执行状态：RUNNING / SUCCESS / FAILED',
    outputs         TEXT            COMMENT '变量快照 JSON',
    error           VARCHAR(500)    NOT NULL DEFAULT '' COMMENT '错误信息',
    elapsed_ms      INT             COMMENT '耗时（毫秒）',
    created_at      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    finished_at     DATETIME(3)     COMMENT '完成时间',

    PRIMARY KEY (id),
    KEY idx_node_run_run_id (workflow_run_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='工作流节点执行记录';

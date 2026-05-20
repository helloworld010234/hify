-- ============================================
-- Hify Chat 模块数据库设计
-- ============================================

-- ------------------------------------------
-- t_chat_session：对话会话主表
-- ------------------------------------------
CREATE TABLE IF NOT EXISTS t_chat_session (
    id          BIGINT          NOT NULL AUTO_INCREMENT COMMENT '主键',
    agent_id    BIGINT          NOT NULL COMMENT '关联的 Agent ID（t_agent.id）',
    title       VARCHAR(200)    NOT NULL DEFAULT '' COMMENT '会话标题（首条用户消息摘要）',
    user_id     BIGINT          COMMENT '用户 ID（预留）',

    created_at  DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at  DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    deleted     TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '逻辑删除标志',

    PRIMARY KEY (id),
    KEY idx_agent_id (agent_id, deleted),
    KEY idx_created_at (created_at, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='对话会话表';

-- ------------------------------------------
-- t_chat_message：对话消息表
-- ------------------------------------------
CREATE TABLE IF NOT EXISTS t_chat_message (
    id          BIGINT          NOT NULL AUTO_INCREMENT COMMENT '主键',
    session_id  BIGINT          NOT NULL COMMENT '所属会话 ID（t_chat_session.id）',
    role        VARCHAR(20)     NOT NULL COMMENT '消息角色：user / assistant / system',
    content     MEDIUMTEXT      NOT NULL COMMENT '消息内容',
    tokens      INT             COMMENT '消息 token 数（用于上下文预算计算）',

    created_at  DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at  DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    deleted     TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '逻辑删除标志',

    PRIMARY KEY (id),
    KEY idx_session_created (session_id, deleted, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='对话消息表';

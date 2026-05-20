-- ============================================
-- Hify Chat 模块数据库设计（H2 兼容版本）
-- ============================================

CREATE TABLE IF NOT EXISTS t_chat_session (
    id          BIGINT          NOT NULL AUTO_INCREMENT PRIMARY KEY,
    agent_id    BIGINT          NOT NULL,
    title       VARCHAR(200)    NOT NULL DEFAULT '',
    user_id     BIGINT,
    created_at  TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted     BOOLEAN         NOT NULL DEFAULT FALSE
);

CREATE INDEX IF NOT EXISTS idx_chat_session_agent ON t_chat_session(agent_id, deleted);
CREATE INDEX IF NOT EXISTS idx_chat_session_created ON t_chat_session(created_at, deleted);

CREATE TABLE IF NOT EXISTS t_chat_message (
    id          BIGINT          NOT NULL AUTO_INCREMENT PRIMARY KEY,
    session_id  BIGINT          NOT NULL,
    role        VARCHAR(20)     NOT NULL,
    content     CLOB            NOT NULL,
    tokens      INT,
    created_at  TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted     BOOLEAN         NOT NULL DEFAULT FALSE
);

CREATE INDEX IF NOT EXISTS idx_chat_message_session ON t_chat_message(session_id, deleted, created_at);

-- ============================================
-- Hify Provider 模块数据库设计（H2 兼容版本）
-- ============================================

CREATE TABLE IF NOT EXISTS t_provider (
    id                  BIGINT          NOT NULL AUTO_INCREMENT PRIMARY KEY,
    code                VARCHAR(32)     NOT NULL,
    name                VARCHAR(64)     NOT NULL,
    provider_type       VARCHAR(32)     NOT NULL,
    base_url            VARCHAR(255)    NOT NULL,
    auth_type           VARCHAR(16)     NOT NULL DEFAULT 'bearer',
    api_key             VARCHAR(512),
    auth_config         CLOB,
    timeout_ms          INT             NOT NULL DEFAULT 90000,
    max_retries         INT             NOT NULL DEFAULT 3,
    status              VARCHAR(16)     NOT NULL DEFAULT 'active',
    health_status       VARCHAR(16)     NOT NULL DEFAULT 'unknown',
    consecutive_failures INT            NOT NULL DEFAULT 0,
    last_check_time     TIMESTAMP,
    last_error_msg      VARCHAR(512),
    fallback_provider_id BIGINT,
    sort_order          INT             NOT NULL DEFAULT 0,
    remark              VARCHAR(255),
    created_at          TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted             BOOLEAN         NOT NULL DEFAULT FALSE
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_code_deleted ON t_provider(code, deleted);
CREATE INDEX IF NOT EXISTS idx_type_status ON t_provider(provider_type, deleted, status);
CREATE INDEX IF NOT EXISTS idx_health_status ON t_provider(health_status, deleted);

CREATE TABLE IF NOT EXISTS t_model (
    id                  BIGINT          NOT NULL AUTO_INCREMENT PRIMARY KEY,
    provider_id         BIGINT          NOT NULL,
    model_code          VARCHAR(64)     NOT NULL,
    model_name          VARCHAR(64)     NOT NULL,
    model_type          VARCHAR(16)     NOT NULL DEFAULT 'chat',
    max_context_tokens  INT,
    max_output_tokens   INT,
    supports_streaming  BOOLEAN         NOT NULL DEFAULT TRUE,
    supports_tool_calls BOOLEAN         NOT NULL DEFAULT FALSE,
    supports_vision     BOOLEAN         NOT NULL DEFAULT FALSE,
    supports_json_mode  BOOLEAN         NOT NULL DEFAULT FALSE,
    input_price         BIGINT,
    output_price        BIGINT,
    extra_params        CLOB,
    status              VARCHAR(16)     NOT NULL DEFAULT 'active',
    is_default          BOOLEAN         NOT NULL DEFAULT FALSE,
    sort_order          INT             NOT NULL DEFAULT 0,
    created_at          TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted             BOOLEAN         NOT NULL DEFAULT FALSE
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_provider_model_deleted ON t_model(provider_id, model_code, deleted);
CREATE INDEX IF NOT EXISTS idx_provider_status ON t_model(provider_id, deleted, status);
CREATE INDEX IF NOT EXISTS idx_model_type ON t_model(model_type, deleted);

CREATE TABLE IF NOT EXISTS t_provider_health (
    id                  BIGINT          NOT NULL AUTO_INCREMENT PRIMARY KEY,
    provider_id         BIGINT          NOT NULL,
    health_status       VARCHAR(16)     NOT NULL DEFAULT 'unknown',
    consecutive_failures INT            NOT NULL DEFAULT 0,
    last_check_time     TIMESTAMP,
    last_error_msg      VARCHAR(512),
    response_time_ms    BIGINT,
    UNIQUE (provider_id)
);

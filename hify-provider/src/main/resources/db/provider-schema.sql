-- ============================================
-- Hify Provider 模块数据库设计
-- 设计原则：
-- 1. 鉴权差异通过 auth_type + auth_config(JSON) 统一抽象
-- 2. 供应商与模型一对多，模型独立管理状态与能力
-- 3. 健康状态与人工状态分离，支持自动熔断
-- ============================================

-- ------------------------------------------
-- t_provider：供应商配置表
-- ------------------------------------------
CREATE TABLE IF NOT EXISTS t_provider (
    id                  BIGINT          NOT NULL AUTO_INCREMENT COMMENT '主键',
    code                VARCHAR(32)     NOT NULL COMMENT '供应商唯一编码，如 openai/deepseek/anthropic/azure/ollama',
    name                VARCHAR(64)     NOT NULL COMMENT '显示名称',
    provider_type       VARCHAR(16)     NOT NULL COMMENT '协议类型：openai_compatible/anthropic/azure_openai/ollama',
    base_url            VARCHAR(255)    NOT NULL COMMENT 'API Base URL，如 https://api.openai.com',

    -- 鉴权统一存储（核心设计）
    auth_type           VARCHAR(16)     NOT NULL DEFAULT 'bearer' COMMENT '鉴权类型：none/bearer/api_key/azure_api_key',
    api_key             VARCHAR(512)    COMMENT '加密存储的主凭证（AES加密，落库前加密，出库后解密）',
    auth_config         JSON            COMMENT '鉴权额外配置（非敏感JSON），如 anthropic-version、azure deployment',

    -- 连接参数
    timeout_ms          INT             NOT NULL DEFAULT 90000 COMMENT '请求超时（毫秒）',
    max_retries         INT             NOT NULL DEFAULT 3 COMMENT '最大重试次数',

    -- 健康与熔断（与 status 分离）
    status              VARCHAR(16)     NOT NULL DEFAULT 'active' COMMENT '人工状态：active(启用)/inactive(禁用)',
    health_status       VARCHAR(16)     NOT NULL DEFAULT 'unknown' COMMENT '自动健康状态：healthy/unhealthy/degraded/unknown',
    consecutive_failures INT            NOT NULL DEFAULT 0 COMMENT '连续失败次数（用于熔断判断，如 >=3 触发熔断）',
    last_check_time     DATETIME(3)     COMMENT '最近一次健康检查时间',
    last_error_msg      VARCHAR(512)    COMMENT '最近一次错误信息（用于运维排查）',
    fallback_provider_id BIGINT         COMMENT '熔断后 fallback 的供应商ID（自关联，指向本表）',

    sort_order          INT             NOT NULL DEFAULT 0 COMMENT '排序',
    remark              VARCHAR(255)    COMMENT '备注',

    created_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    deleted             TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '逻辑删除标志',

    PRIMARY KEY (id),
    UNIQUE KEY uk_code_deleted (code, deleted),
    KEY idx_type_status (provider_type, deleted, status),
    KEY idx_health_status (health_status, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='LLM 供应商配置表';

-- ------------------------------------------
-- t_model：模型配置表
-- 一个供应商下挂载多个模型，模型有独立的状态与能力开关
-- ------------------------------------------
CREATE TABLE IF NOT EXISTS t_model (
    id                  BIGINT          NOT NULL AUTO_INCREMENT COMMENT '主键',
    provider_id         BIGINT          NOT NULL COMMENT '所属供应商ID（t_provider.id）',
    model_code          VARCHAR(64)     NOT NULL COMMENT '模型唯一标识，如 gpt-4o/deepseek-chat/claude-sonnet-4',
    model_name          VARCHAR(64)     NOT NULL COMMENT '显示名称',
    model_type          VARCHAR(16)     NOT NULL DEFAULT 'chat' COMMENT '模型类型：chat/embedding/vision/reasoning/multimodal',

    -- 能力参数（前端根据这些开关展示不同交互）
    max_context_tokens  INT             COMMENT '最大上下文Token数',
    max_output_tokens   INT             COMMENT '最大输出Token数',
    supports_streaming  TINYINT(1)      NOT NULL DEFAULT 1 COMMENT '是否支持流式输出',
    supports_tool_calls TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '是否支持工具调用（Function Calling）',
    supports_vision     TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '是否支持视觉输入（图片理解）',
    supports_json_mode  TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '是否支持JSON模式/结构化输出',

    -- 价格（单位：分/百万token，便于精确计算和前端展示）
    input_price         BIGINT          COMMENT '输入价格（每百万token，单位：分，如 200 = 2元）',
    output_price        BIGINT          COMMENT '输出价格（每百万token，单位：分）',

    -- 状态管理
    status              VARCHAR(16)     NOT NULL DEFAULT 'active' COMMENT '模型状态：active(启用)/inactive(禁用)/deprecated(废弃)',
    is_default          TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '是否该供应商下的默认模型（一个供应商下仅一个默认）',
    sort_order          INT             NOT NULL DEFAULT 0 COMMENT '排序',

    created_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    deleted             TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '逻辑删除标志',

    PRIMARY KEY (id),
    UNIQUE KEY uk_provider_model_deleted (provider_id, model_code, deleted),
    KEY idx_provider_status (provider_id, deleted, status),
    KEY idx_model_type (model_type, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='LLM 模型配置表';

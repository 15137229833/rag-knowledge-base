-- API 开放平台相关表
-- 支持 API Token 认证和第三方集成

-- API Token 表
CREATE TABLE IF NOT EXISTS api_token (
    id UUID PRIMARY KEY,
    token VARCHAR(255) UNIQUE NOT NULL,
    name VARCHAR(255) NOT NULL,
    user_id UUID NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    app_name VARCHAR(255),
    app_description TEXT,
    is_active BOOLEAN DEFAULT TRUE,
    expires_at TIMESTAMP,
    last_used_at TIMESTAMP,
    rate_limit_per_minute INT DEFAULT 60,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- API 使用日志表
CREATE TABLE IF NOT EXISTS api_usage_log (
    id UUID PRIMARY KEY,
    token_id UUID REFERENCES api_token(id) ON DELETE CASCADE,
    endpoint VARCHAR(500) NOT NULL,
    method VARCHAR(10) NOT NULL,
    status_code INT NOT NULL,
    response_time_ms INT,
    ip_address VARCHAR(45),
    user_agent TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Webhook 表
CREATE TABLE IF NOT EXISTS webhook (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    name VARCHAR(255) NOT NULL,
    url VARCHAR(1000) NOT NULL,
    secret VARCHAR(255),
    events TEXT[] NOT NULL, -- ['document.ingested', 'document.failed']
    is_active BOOLEAN DEFAULT TRUE,
    last_triggered_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Webhook 触发日志表
CREATE TABLE IF NOT EXISTS webhook_trigger_log (
    id UUID PRIMARY KEY,
    webhook_id UUID REFERENCES webhook(id) ON DELETE CASCADE,
    event_type VARCHAR(100) NOT NULL,
    payload JSONB,
    status VARCHAR(50) NOT NULL, -- 'success', 'failed', 'retrying'
    status_code INT,
    response_body TEXT,
    retry_count INT DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 创建索引
CREATE INDEX IF NOT EXISTS idx_api_token_user ON api_token(user_id);
CREATE INDEX IF NOT EXISTS idx_api_token_token ON api_token(token);
CREATE INDEX IF NOT EXISTS idx_api_token_active ON api_token(is_active, expires_at);
CREATE INDEX IF NOT EXISTS idx_api_usage_log_token_time ON api_usage_log(token_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_webhook_user ON webhook(user_id);
CREATE INDEX IF NOT EXISTS idx_webhook_trigger_log_webhook_time ON webhook_trigger_log(webhook_id, created_at DESC);

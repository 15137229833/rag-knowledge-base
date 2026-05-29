CREATE TABLE IF NOT EXISTS app_config (
    config_key VARCHAR(128) PRIMARY KEY,
    config_value TEXT NOT NULL,
    updated_by UUID,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS prompt_template (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    name VARCHAR(128) NOT NULL,
    description VARCHAR(512),
    template_text TEXT NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT true,
    created_by UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_prompt_template_name ON prompt_template (name);
CREATE INDEX IF NOT EXISTS idx_prompt_template_enabled ON prompt_template (enabled);
CREATE INDEX IF NOT EXISTS idx_prompt_template_updated_at ON prompt_template (updated_at DESC);

CREATE TABLE chat_record (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    kb_id UUID NOT NULL REFERENCES knowledge_base (id) ON DELETE CASCADE,
    question TEXT NOT NULL,
    answer TEXT NOT NULL,
    citations_json TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_chat_record_user_kb_time ON chat_record (user_id, kb_id, created_at DESC);

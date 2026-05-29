CREATE TABLE chat_session (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    kb_id UUID NOT NULL REFERENCES knowledge_base (id) ON DELETE CASCADE,
    title VARCHAR(64),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_chat_session_user_kb ON chat_session (user_id, kb_id, created_at DESC);

ALTER TABLE chat_record
    ADD COLUMN session_id UUID REFERENCES chat_session (id) ON DELETE SET NULL;

CREATE INDEX idx_chat_record_session ON chat_record (session_id);

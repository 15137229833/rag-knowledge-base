ALTER TABLE chat_record
    ADD COLUMN helpful BOOLEAN,
    ADD COLUMN feedback_note TEXT,
    ADD COLUMN feedback_at TIMESTAMPTZ;

CREATE INDEX idx_chat_record_feedback_time
    ON chat_record (feedback_at DESC);

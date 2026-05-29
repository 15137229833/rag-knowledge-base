ALTER TABLE kb_document
    ADD COLUMN IF NOT EXISTS content_hash VARCHAR(64);

CREATE INDEX IF NOT EXISTS idx_kb_document_hash ON kb_document (kb_id, content_hash);

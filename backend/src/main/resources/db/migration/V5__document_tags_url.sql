ALTER TABLE kb_document
    ADD COLUMN tags TEXT,
    ADD COLUMN source_url TEXT;

UPDATE kb_document SET tags = '[]' WHERE tags IS NULL;

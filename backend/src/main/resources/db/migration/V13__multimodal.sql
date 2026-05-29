-- 多模态支持相关表
-- 支持图片、表格、公式的解析和检索

-- 图片块表
CREATE TABLE IF NOT EXISTS image_block (
    id UUID PRIMARY KEY,
    document_id UUID NOT NULL REFERENCES kb_document(id) ON DELETE CASCADE,
    page_number INT NOT NULL,
    image_data BYTEA,
    ocr_text TEXT,
    description TEXT,
    embedding vector(768),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 表格块表
CREATE TABLE IF NOT EXISTS table_block (
    id UUID PRIMARY KEY,
    document_id UUID NOT NULL REFERENCES kb_document(id) ON DELETE CASCADE,
    page_number INT NOT NULL,
    table_data JSONB NOT NULL,
    markdown_text TEXT,
    description TEXT,
    embedding vector(768),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 公式块表
CREATE TABLE IF NOT EXISTS formula_block (
    id UUID PRIMARY KEY,
    document_id UUID NOT NULL REFERENCES kb_document(id) ON DELETE CASCADE,
    page_number INT NOT NULL,
    latex_formula TEXT NOT NULL,
    description TEXT,
    embedding vector(768),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 创建索引
CREATE INDEX IF NOT EXISTS idx_image_block_document ON image_block(document_id);
CREATE INDEX IF NOT EXISTS idx_table_block_document ON table_block(document_id);
CREATE INDEX IF NOT EXISTS idx_formula_block_document ON formula_block(document_id);
CREATE INDEX IF NOT EXISTS idx_image_block_embedding ON image_block USING ivfflat(embedding vector_cosine_ops) WITH (lists = 100);
CREATE INDEX IF NOT EXISTS idx_table_block_embedding ON table_block USING ivfflat(embedding vector_cosine_ops) WITH (lists = 100);
CREATE INDEX IF NOT EXISTS idx_formula_block_embedding ON formula_block USING ivfflat(embedding vector_cosine_ops) WITH (lists = 100);

-- 协作功能相关表
-- 支持多人协作编辑文档和知识库

-- 文档评论表
CREATE TABLE IF NOT EXISTS document_comment (
    id UUID PRIMARY KEY,
    document_id UUID NOT NULL REFERENCES kb_document(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    content TEXT NOT NULL,
    position INT, -- 在文档中的位置
    parent_comment_id UUID REFERENCES document_comment(id) ON DELETE CASCADE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 文档版本表
CREATE TABLE IF NOT EXISTS document_version (
    id UUID PRIMARY KEY,
    document_id UUID NOT NULL REFERENCES kb_document(id) ON DELETE CASCADE,
    version_number INT NOT NULL,
    content_hash VARCHAR(64) NOT NULL,
    change_log TEXT,
    created_by UUID NOT NULL REFERENCES app_user(id),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(document_id, version_number)
);

-- 创建索引
CREATE INDEX IF NOT EXISTS idx_document_comment_document ON document_comment(document_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_document_comment_user ON document_comment(user_id);
CREATE INDEX IF NOT EXISTS idx_document_version_document ON document_version(document_id, version_number DESC);

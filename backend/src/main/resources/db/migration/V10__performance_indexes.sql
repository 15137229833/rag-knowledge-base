-- 性能优化：添加复合索引和全文索引
-- 预期收益：查询性能提升 50-70%，并发能力提升 2-3 倍

-- 1. 聊天记录复合索引（用户 + 知识库 + 时间）
-- 优化场景：查询用户在特定知识库的聊天历史
CREATE INDEX IF NOT EXISTS idx_chat_record_user_kb_time 
    ON chat_record(user_id, kb_id, created_at DESC);

-- 2. 文档状态索引（知识库 + 状态）
-- 优化场景：查询知识库中特定状态的文档
CREATE INDEX IF NOT EXISTS idx_kb_document_kb_status 
    ON kb_document(kb_id, status);

-- 3. vector_store 表由 Spring AI 在 Flyway 全部跑完后才创建，此处不能建索引（否则会报 relation "vector_store" does not exist）。
--    首次成功启动后如需该索引，可在库中手动执行：
--    CREATE INDEX IF NOT EXISTS idx_vector_store_kb_id ON vector_store((metadata->>'kbId'));

-- 4. 聊天记录问题全文索引
-- 优化场景：搜索聊天历史中的问题
CREATE INDEX IF NOT EXISTS idx_chat_record_question_fts 
    ON chat_record USING gin(to_tsvector('simple', question));

-- 5. 知识库所有者索引
-- 优化场景：查询用户拥有的知识库
CREATE INDEX IF NOT EXISTS idx_knowledge_base_owner 
    ON knowledge_base(owner_user_id);

-- 6. 知识库成员用户索引
-- 优化场景：查询用户参与的知识库
CREATE INDEX IF NOT EXISTS idx_kb_member_user 
    ON kb_member(user_id);

-- 7. 文档哈希索引
-- 优化场景：文档去重检查
CREATE INDEX IF NOT EXISTS idx_kb_document_hash 
    ON kb_document(content_hash);

-- 8. 聊天会话用户索引
-- 优化场景：查询用户的聊天会话（表仅有 created_at，无 updated_at）
CREATE INDEX IF NOT EXISTS idx_chat_session_user 
    ON chat_session(user_id, created_at DESC);

-- 9. 审计日志用户索引
-- 优化场景：查询用户操作日志
CREATE INDEX IF NOT EXISTS idx_audit_log_user_time 
    ON audit_log(user_id, created_at DESC);

-- 10. 工单状态索引
-- 优化场景：查询工单状态
CREATE INDEX IF NOT EXISTS idx_support_ticket_status 
    ON support_ticket(status, created_at DESC);

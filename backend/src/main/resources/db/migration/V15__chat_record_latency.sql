-- 单次问答端到端耗时（毫秒），用于分析统计
ALTER TABLE chat_record
    ADD COLUMN IF NOT EXISTS latency_ms BIGINT;

COMMENT ON COLUMN chat_record.latency_ms IS '问答耗时（毫秒），服务端自收到请求至生成完成';

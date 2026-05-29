-- 个性化问答相关表
-- 支持用户偏好设置和个性化 Prompt 生成

-- 用户偏好表
CREATE TABLE IF NOT EXISTS user_preference (
    id UUID PRIMARY KEY,
    user_id UUID UNIQUE NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    answer_style VARCHAR(20) DEFAULT 'concise', -- 'concise', 'detailed', 'conversational'
    language VARCHAR(10) DEFAULT 'zh', -- 'zh', 'en'
    expertise_level VARCHAR(20) DEFAULT 'intermediate', -- 'beginner', 'intermediate', 'advanced', 'expert'
    favorite_topics TEXT[], -- 用户偏好主题列表
    preferred_response_length INT DEFAULT 500, -- 偏好回答长度
    include_examples BOOLEAN DEFAULT true, -- 是否包含示例
    include_references BOOLEAN DEFAULT true, -- 是否包含引用
    tone VARCHAR(20) DEFAULT 'professional', -- 'professional', 'friendly', 'formal', 'casual'
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 用户反馈表（用于学习用户偏好）
CREATE TABLE IF NOT EXISTS user_feedback (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    chat_record_id UUID REFERENCES chat_record(id) ON DELETE CASCADE,
    feedback_type VARCHAR(20) NOT NULL, -- 'helpful', 'not_helpful', 'too_long', 'too_short', 'inaccurate'
    rating INT CHECK (rating >= 1 AND rating <= 5),
    comment TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 个性化 Prompt 模板表
CREATE TABLE IF NOT EXISTS personalized_prompt_template (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    name VARCHAR(255) NOT NULL,
    template TEXT NOT NULL,
    is_active BOOLEAN DEFAULT true,
    usage_count INT DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 创建索引
CREATE INDEX IF NOT EXISTS idx_user_preference_user ON user_preference(user_id);
CREATE INDEX IF NOT EXISTS idx_user_feedback_user ON user_feedback(user_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_user_feedback_chat ON user_feedback(chat_record_id);
CREATE INDEX IF NOT EXISTS idx_personalized_prompt_user ON personalized_prompt_template(user_id);

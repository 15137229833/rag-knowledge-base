package com.rag.kb.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 大模型接入模式：当前默认使用 OpenAI 兼容接口（Qwen / DashScope），保留 Ollama 作为可选回退。
 */
@Component
public class LlmRuntimeMode {

    @Value("${app.llm.provider:qwen}")
    private String provider;

    /** 兼容旧配置：控制对话端 */
    @Value("${app.llm.chat-backend:openai}")
    private String chatBackend;

    /** 是否整栈使用 DashScope / Qwen */
    public boolean isDashScopeStack() {
        String p = provider == null ? "" : provider.strip();
        return "dashscope".equalsIgnoreCase(p) || "qwen".equalsIgnoreCase(p);
    }

    /** 对话是否走 OpenAI 兼容客户端（DashScope 或 OpenAI 官方） */
    public boolean useOpenAiChat() {
        String cb = chatBackend == null ? "" : chatBackend.strip();
        return isDashScopeStack() || "openai".equalsIgnoreCase(cb);
    }
}

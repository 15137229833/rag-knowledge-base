package com.rag.kb.config;

import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.ollama.api.OllamaOptions;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.stereotype.Component;

/**
 * 按 {@link LlmRuntimeMode} 生成对应厂商的 ChatOptions（温度、topP、topK）。
 */
@Component
public class LlmRuntimeChatOptions {

    private final LlmRuntimeMode mode;

    public LlmRuntimeChatOptions(LlmRuntimeMode mode) {
        this.mode = mode;
    }

    public ChatOptions forChat(double temperature, double topP, int topK) {
        if (mode.useOpenAiChat()) {
            // OpenAI 兼容 API 使用 temperature / topP（与 Ollama 的 topK 并非同一参数，此处不传 topK）
            return OpenAiChatOptions.builder()
                    .temperature(temperature)
                    .topP(topP)
                    .build();
        }
        return OllamaOptions.builder().temperature(temperature).topP(topP).topK(topK).build();
    }

    /** 会话标题生成：低温 */
    public ChatOptions forTitle() {
        if (mode.useOpenAiChat()) {
            return OpenAiChatOptions.builder().temperature(0.2).topP(0.85).build();
        }
        return OllamaOptions.builder().temperature(0.2).topP(0.85).build();
    }
}

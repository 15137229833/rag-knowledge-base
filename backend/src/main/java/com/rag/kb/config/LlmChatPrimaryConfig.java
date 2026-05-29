package com.rag.kb.config;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * 对话模型：{@link LlmRuntimeMode#useOpenAiChat()} 为 true 时使用 OpenAI 兼容客户端（DashScope qwen-plus 等），否则
 * Ollama。
 */
@Configuration
public class LlmChatPrimaryConfig {

    @Bean
    @Primary
    public ChatModel primaryChatModel(
            LlmRuntimeMode mode,
            ObjectProvider<OpenAiChatModel> openAiChatModel,
            ObjectProvider<OllamaChatModel> ollamaChatModel) {
        if (mode.useOpenAiChat()) {
            OpenAiChatModel m = openAiChatModel.getIfAvailable();
            if (m == null) {
                throw new IllegalStateException(
                        "当前配置需要 OpenAI 兼容对话，但未注册 OpenAiChatModel。"
                                + " 请在 backend/llm-local.properties 中配置 spring.ai.openai.api-key，"
                                + "或设置 app.llm.provider=ollama 使用本机 Ollama。");
            }
            return m;
        }
        return ollamaChatModel.getObject();
    }
}

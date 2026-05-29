package com.rag.kb.service;

import com.rag.kb.config.MultimodalProperties;
import com.rag.kb.config.RagRetrievalProperties;
import com.rag.kb.dto.SystemDtos.RuntimeSummaryResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RuntimeSummaryService {

    private final MultimodalProperties multimodalProperties;
    private final RagRetrievalProperties ragRetrievalProperties;

    @Value("${app.llm.provider:qwen}")
    private String llmProvider;

    @Value("${app.llm.chat-backend:openai}")
    private String chatBackend;

    @Value("${spring.ai.openai.base-url:https://dashscope.aliyuncs.com/compatible-mode/v1}")
    private String openAiBaseUrl;

    @Value("${spring.ai.openai.chat.options.model:qwen-plus}")
    private String openAiChatModel;

    @Value("${spring.ai.ollama.base-url:http://localhost:11434}")
    private String ollamaBaseUrl;

    @Value("${spring.ai.ollama.chat.options.model:llama3.2}")
    private String ollamaChatModel;

    public RuntimeSummaryResponse getSummary() {
        String chatModel = "ollama".equalsIgnoreCase(chatBackend) ? ollamaChatModel : openAiChatModel;
        String baseUrl = "ollama".equalsIgnoreCase(chatBackend) ? ollamaBaseUrl : openAiBaseUrl;
        String retrievalMode = ragRetrievalProperties.getHybrid().getFtsWeight() >= 1.0d
                ? "FTS-only"
                : (ragRetrievalProperties.getHybrid().isEnabled() ? "hybrid" : "vector-only");

        return new RuntimeSummaryResponse(
                llmProvider,
                chatBackend,
                chatModel,
                baseUrl,
                multimodalProperties.getVisionProvider(),
                multimodalProperties.getOpenAiVisionModel(),
                multimodalProperties.getOllamaVisionModel(),
                multimodalProperties.isImageCaptionEnabled(),
                multimodalProperties.isVideoCaptionEnabled(),
                retrievalMode,
                ragRetrievalProperties.getVectorTable());
    }
}

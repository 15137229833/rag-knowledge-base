package com.rag.kb.config;

import com.rag.kb.dto.SystemDtos.RuntimeSummaryResponse;
import com.rag.kb.service.RuntimeSummaryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
@Slf4j
public class ModelRuntimeSummaryConfig {

    private final RuntimeSummaryService runtimeSummaryService;

    @Bean
    ApplicationRunner modelRuntimeSummaryRunner() {
        return args -> {
            RuntimeSummaryResponse summary = runtimeSummaryService.getSummary();
            log.info("==== Runtime Model Summary ====");
            log.info("LLM provider: {}", summary.llmProvider());
            log.info("Chat backend: {}", summary.chatBackend());
            log.info("Chat model: {}", summary.chatModel());
            log.info("Base URL: {}", summary.baseUrl());
            log.info("Vision provider: {}", summary.visionProvider());
            log.info("Vision model (OpenAI): {}", summary.openAiVisionModel());
            log.info("Vision model (Ollama): {}", summary.ollamaVisionModel());
            log.info("Image caption enabled: {}", summary.imageCaptionEnabled());
            log.info("Video caption enabled: {}", summary.videoCaptionEnabled());
            log.info("Retrieval mode: {}", summary.retrievalMode());
            log.info("Chunk table: {}", summary.chunkTable());
            log.info("===============================");
        };
    }
}

package com.rag.kb.service;

import com.rag.kb.config.AsyncExecutorConfig;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * 独立异步代理 Bean，避免同类内部调用 @Async 导致代理失效。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DocumentIngestionAsyncExecutor {

    private final IngestionService ingestionService;

    @Async(AsyncExecutorConfig.INGESTION_EXECUTOR)
    public CompletableFuture<Void> scheduleIngest(UUID documentId) {
        try {
            ingestionService.ingest(documentId);
            return CompletableFuture.completedFuture(null);
        } catch (Exception e) {
            log.error("异步文档摄取失败 documentId={}", documentId, e);
            CompletableFuture<Void> f = new CompletableFuture<>();
            f.completeExceptionally(e);
            return f;
        }
    }
}

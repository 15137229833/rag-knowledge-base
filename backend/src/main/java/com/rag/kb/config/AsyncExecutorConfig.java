package com.rag.kb.config;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * 文档解析与向量入库专用线程池，避免默认 SimpleAsyncTaskExecutor 无界起线程。
 * 与 {@link com.rag.kb.service.DocumentIngestionRunner#scheduleIngest} 的 @Async 名称一致。
 */
@Configuration
public class AsyncExecutorConfig {

    public static final String INGESTION_EXECUTOR = "ingestionTaskExecutor";

    @Bean(name = INGESTION_EXECUTOR)
    public Executor ingestionTaskExecutor() {
        ThreadPoolTaskExecutor ex = new ThreadPoolTaskExecutor();
        ex.setCorePoolSize(2);
        ex.setMaxPoolSize(8);
        ex.setQueueCapacity(500);
        ex.setThreadNamePrefix("doc-ingest-");
        ex.setWaitForTasksToCompleteOnShutdown(true);
        ex.setAwaitTerminationSeconds(60);
        ex.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        ex.initialize();
        return ex;
    }
}

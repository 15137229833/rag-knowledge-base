package com.rag.kb.rag;

import com.rag.kb.config.RagRetrievalProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 应用启动后确保 FTS-only 文本块表及索引存在。
 */
@Component
@Order(Integer.MAX_VALUE)
@RequiredArgsConstructor
@Slf4j
public class VectorStoreFtsIndexRunner implements ApplicationRunner {

    private final TextChunkStoreRepository textChunkStoreRepository;
    private final RagRetrievalProperties ragProps;

    @Override
    public void run(ApplicationArguments args) {
        if (ragProps.getVectorTable() == null || ragProps.getVectorTable().isBlank()) {
            return;
        }
        try {
            textChunkStoreRepository.ensureTable();
        } catch (Exception e) {
            log.warn("Skip FTS chunk table ensure on {}: {}", ragProps.getVectorTable(), e.getMessage());
        }
    }
}

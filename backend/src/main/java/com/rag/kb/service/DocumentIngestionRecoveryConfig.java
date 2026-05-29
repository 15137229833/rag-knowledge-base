package com.rag.kb.service;

import com.rag.kb.domain.DocumentStatus;
import com.rag.kb.domain.KbDocument;
import com.rag.kb.rag.TextChunkStoreRepository;
import com.rag.kb.repository.KbDocumentRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.annotation.Transactional;

/**
 * 应用启动后恢复历史遗留的待处理 / 处理中 / 失败任务，避免重启后文档永久卡住。
 */
@Configuration
@RequiredArgsConstructor
@Slf4j
public class DocumentIngestionRecoveryConfig {

    private final KbDocumentRepository kbDocumentRepository;
    private final DocumentIngestionRunner documentIngestionRunner;
    private final TextChunkStoreRepository textChunkStoreRepository;

    @Bean
    ApplicationRunner documentIngestionRecoveryRunner() {
        return args -> recoverPendingDocuments();
    }

    @Transactional
    void recoverPendingDocuments() {
        cleanupOrphanChunks();
        List<KbDocument> pendingDocs = kbDocumentRepository.findByStatus(DocumentStatus.PENDING);
        List<KbDocument> processingDocs = kbDocumentRepository.findByStatus(DocumentStatus.PROCESSING);
        List<KbDocument> failedDocs = kbDocumentRepository.findByStatus(DocumentStatus.FAILED);

        List<KbDocument> toRetry = new ArrayList<>();
        if (pendingDocs != null) {
            toRetry.addAll(pendingDocs);
        }
        if (processingDocs != null) {
            toRetry.addAll(processingDocs);
        }
        if (failedDocs != null) {
            toRetry.addAll(failedDocs);
        }

        if (toRetry.isEmpty()) {
            return;
        }

        for (KbDocument doc : toRetry) {
            doc.setStatus(DocumentStatus.PENDING);
            doc.setErrorMessage(null);
        }
        kbDocumentRepository.saveAll(toRetry);

        log.info("恢复文档摄取任务 {} 个（pending={} processing={} failed={}）", toRetry.size(),
                pendingDocs == null ? 0 : pendingDocs.size(),
                processingDocs == null ? 0 : processingDocs.size(),
                failedDocs == null ? 0 : failedDocs.size());

        for (KbDocument doc : toRetry) {
            documentIngestionRunner.scheduleAfterCommit(doc.getId());
        }
    }

    void cleanupOrphanChunks() {
        List<UUID> existingDocIds = kbDocumentRepository.findAll().stream().map(KbDocument::getId).toList();
        int removed = textChunkStoreRepository.deleteOrphanChunks(existingDocIds);
        if (removed > 0) {
            log.info("清理历史遗留孤儿文本块 {} 条", removed);
        }
    }
}

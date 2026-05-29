package com.rag.kb.service;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 文档摄取在事务提交后放入异步执行，避免上传事务未提交就查不到文档。
 */
@Component
@RequiredArgsConstructor
public class DocumentIngestionRunner {

    private final DocumentIngestionAsyncExecutor asyncExecutor;

    public void scheduleAfterCommit(UUID documentId) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    asyncExecutor.scheduleIngest(documentId);
                }
            });
            return;
        }
        asyncExecutor.scheduleIngest(documentId);
    }
}

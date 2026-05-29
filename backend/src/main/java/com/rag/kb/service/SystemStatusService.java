package com.rag.kb.service;

import com.rag.kb.config.AppProperties;
import com.rag.kb.dto.SystemDtos.ServiceHealth;
import com.rag.kb.dto.SystemDtos.SystemStatusResponse;
import com.rag.kb.repository.AppUserRepository;
import com.rag.kb.repository.AuditLogRepository;
import com.rag.kb.repository.KbDocumentRepository;
import com.rag.kb.repository.KnowledgeBaseRepository;
import com.rag.kb.repository.PromptTemplateRepository;
import io.minio.BucketExistsArgs;
import io.minio.MinioClient;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

@Service
@RequiredArgsConstructor
public class SystemStatusService {

    private final AppUserRepository appUserRepository;
    private final KnowledgeBaseRepository knowledgeBaseRepository;
    private final KbDocumentRepository kbDocumentRepository;
    private final PromptTemplateRepository promptTemplateRepository;
    private final AuditLogRepository auditLogRepository;
    private final MinioClient minioClient;
    private final AppProperties appProperties;

    @Value("${spring.ai.ollama.base-url:http://localhost:11434}")
    private String ollamaBaseUrl;

    private final long bootMillis = System.currentTimeMillis();

    @Transactional(readOnly = true)
    public SystemStatusResponse status() {
        List<ServiceHealth> services = new ArrayList<>();

        services.add(checkDatabase());
        services.add(checkMinio());
        services.add(checkOllama());

        Instant since = Instant.now().minus(24, ChronoUnit.HOURS);
        long chatCalls = auditLogRepository.countByActionSince("CHAT", since);
        long uploadCalls = auditLogRepository.countByActionSince("DOC_UPLOAD", since)
                + auditLogRepository.countByActionSince("DOC_URL_IMPORT", since);
        long errors = auditLogRepository.countByActionSince("ERROR", since);

        return new SystemStatusResponse(
                Instant.now(),
                (System.currentTimeMillis() - bootMillis) / 1000,
                services,
                appUserRepository.count(),
                knowledgeBaseRepository.count(),
                kbDocumentRepository.count(),
                promptTemplateRepository.count(),
                chatCalls,
                uploadCalls,
                errors);
    }

    private ServiceHealth checkDatabase() {
        try {
            long n = appUserRepository.count();
            return new ServiceHealth("database", true, "ok, users=" + n);
        } catch (Exception e) {
            return new ServiceHealth("database", false, e.getMessage());
        }
    }

    private ServiceHealth checkMinio() {
        try {
            boolean exists = minioClient.bucketExists(
                    BucketExistsArgs.builder().bucket(appProperties.getMinio().getBucket()).build());
            return new ServiceHealth("minio", exists, exists ? "bucket ok" : "bucket missing");
        } catch (Exception e) {
            return new ServiceHealth("minio", false, e.getMessage());
        }
    }

    private ServiceHealth checkOllama() {
        try {
            RestClient client = RestClient.builder().baseUrl(ollamaBaseUrl).build();
            client.get().uri("/api/tags").retrieve().toBodilessEntity();
            return new ServiceHealth("ollama", true, "ok");
        } catch (Exception e) {
            return new ServiceHealth("ollama", false, e.getMessage());
        }
    }
}

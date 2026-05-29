package com.rag.kb.service;

import com.rag.kb.domain.AppUser;
import com.rag.kb.domain.AuditLog;
import com.rag.kb.repository.AppUserRepository;
import com.rag.kb.repository.AuditLogRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditLogRepository auditLogRepository;
    private final AppUserRepository appUserRepository;

    @Async
    public void logByUserId(UUID userId, String action, String resourceType, String resourceId, String detailJson) {
        AppUser user = userId == null ? null : appUserRepository.getReferenceById(userId);
        auditLogRepository.save(new AuditLog(user, action, resourceType, resourceId, detailJson));
    }
}

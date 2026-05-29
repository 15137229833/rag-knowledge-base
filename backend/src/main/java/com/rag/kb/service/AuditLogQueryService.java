package com.rag.kb.service;

import com.rag.kb.domain.AuditLog;
import com.rag.kb.dto.AuditDtos.AuditLogRow;
import com.rag.kb.dto.AuditDtos.PagedResponse;
import com.rag.kb.repository.AuditLogRepository;
import jakarta.persistence.criteria.Predicate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuditLogQueryService {

    private final AuditLogRepository auditLogRepository;

    @Transactional(readOnly = true)
    public PagedResponse<AuditLogRow> page(int page, int size, String action, Instant from, Instant to) {
        int p = Math.max(0, page);
        int s = Math.min(100, Math.max(1, size));
        try {
            if (from != null && to != null && from.isAfter(to)) {
                return PagedResponse.empty(p, s);
            }
            String actionEq = normalize(action);
            Specification<AuditLog> spec = (root, query, cb) -> {
                List<Predicate> ps = new ArrayList<>();
                if (actionEq != null) {
                    ps.add(cb.equal(root.get("action"), actionEq));
                }
                if (from != null) {
                    ps.add(cb.greaterThanOrEqualTo(root.get("createdAt"), from));
                }
                if (to != null) {
                    ps.add(cb.lessThanOrEqualTo(root.get("createdAt"), to));
                }
                return ps.isEmpty() ? cb.conjunction() : cb.and(ps.toArray(Predicate[]::new));
            };

            Page<AuditLog> pg =
                    auditLogRepository.findAll(
                            spec, PageRequest.of(p, s, Sort.by(Sort.Direction.DESC, "createdAt")));
            List<AuditLogRow> rows =
                    pg.getContent().stream().map(this::toRowSafe).toList();
            return PagedResponse.of(pg, rows);
        } catch (Exception e) {
            log.warn("审计日志分页查询失败 page={} size={}: {}", p, s, e.toString());
            return PagedResponse.empty(p, s);
        }
    }

    private static String normalize(String action) {
        if (action == null) return null;
        String a = action.trim();
        return a.isEmpty() ? null : a;
    }

    private AuditLogRow toRowSafe(AuditLog log) {
        if (log == null) {
            return new AuditLogRow(0L, null, null, "", null, null, null, null);
        }
        var u = log.getUser();
        return new AuditLogRow(
                log.getId() != null ? log.getId() : 0L,
                u != null ? u.getId() : null,
                u != null ? u.getUsername() : null,
                log.getAction() != null ? log.getAction() : "",
                log.getResourceType(),
                log.getResourceId(),
                log.getDetailJson(),
                log.getCreatedAt());
    }
}

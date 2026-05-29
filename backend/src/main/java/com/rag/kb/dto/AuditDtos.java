package com.rag.kb.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class AuditDtos {

    private AuditDtos() {}

    public record AuditLogRow(
            Long id,
            UUID userId,
            String username,
            String action,
            String resourceType,
            String resourceId,
            String detail,
            Instant createdAt) {}

    public record PagedResponse<T>(
            List<T> content, long totalElements, int totalPages, int number, int size) {
        public static <T> PagedResponse<T> of(
                org.springframework.data.domain.Page<?> page, List<T> content) {
            return new PagedResponse<>(
                    content,
                    page.getTotalElements(),
                    page.getTotalPages(),
                    page.getNumber(),
                    page.getSize());
        }

        /** 查询异常或空结果时的安全占位，避免 500 */
        public static <T> PagedResponse<T> empty(int number, int size) {
            return new PagedResponse<>(List.of(), 0, 0, Math.max(0, number), Math.max(1, size));
        }
    }
}

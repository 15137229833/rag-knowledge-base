package com.rag.kb.api;

import com.rag.kb.dto.AuditDtos.AuditLogRow;
import com.rag.kb.dto.AuditDtos.PagedResponse;
import com.rag.kb.service.AuditLogQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/audit-logs")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "AdminAudit", description = "管理员审计日志接口")
public class AdminAuditController {

    private final AuditLogQueryService auditLogQueryService;

    @GetMapping
    @Operation(summary = "分页查询审计日志（管理员）")
    public PagedResponse<AuditLogRow> page(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to) {
        return auditLogQueryService.page(page, size, action, from, to);
    }
}

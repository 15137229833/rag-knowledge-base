package com.rag.kb.repository;

import com.rag.kb.domain.AuditLog;
import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long>, JpaSpecificationExecutor<AuditLog> {

    @Query("""
            SELECT COUNT(a) FROM AuditLog a
            WHERE a.action = :action AND a.createdAt >= :since
            """)
    long countByActionSince(@Param("action") String action, @Param("since") Instant since);
}

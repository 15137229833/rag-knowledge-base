package com.rag.kb.repository;

import com.rag.kb.domain.KbDocument;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface KbDocumentRepository extends JpaRepository<KbDocument, UUID> {

    List<KbDocument> findByKnowledgeBase_IdOrderByCreatedAtDesc(UUID kbId);

    List<KbDocument> findByStatus(com.rag.kb.domain.DocumentStatus status);

    Optional<KbDocument> findByKnowledgeBase_IdAndContentHash(UUID kbId, String contentHash);

    long countByKnowledgeBase_Id(UUID kbId);

    @Query("SELECT MAX(d.createdAt) FROM KbDocument d WHERE d.knowledgeBase.id = :kbId")
    Optional<Instant> findMaxCreatedAtByKbId(@Param("kbId") UUID kbId);

    // 新增方法用于推荐和分析
    List<KbDocument> findByKnowledgeBaseIdAndStatus(UUID kbId, com.rag.kb.domain.DocumentStatus status);
    
    org.springframework.data.domain.Page<KbDocument> findByKnowledgeBaseIdAndStatusOrderByCreatedAtDesc(
            UUID kbId, com.rag.kb.domain.DocumentStatus status, org.springframework.data.domain.Pageable pageable);
    
    long countByCreatedAtBetween(Instant start, Instant end);
    
    long countByKnowledgeBaseId(UUID kbId);
    
    long countByKnowledgeBaseIdAndStatus(UUID kbId, com.rag.kb.domain.DocumentStatus status);
}

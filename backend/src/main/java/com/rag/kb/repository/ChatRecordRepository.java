package com.rag.kb.repository;

import com.rag.kb.domain.ChatRecord;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChatRecordRepository extends JpaRepository<ChatRecord, UUID> {

    @EntityGraph(value = "ChatRecord.withSession", type = EntityGraph.EntityGraphType.FETCH)
    Page<ChatRecord> findByUser_IdAndKnowledgeBase_IdOrderByCreatedAtDesc(
            UUID userId, UUID kbId, Pageable pageable);

    @EntityGraph(value = "ChatRecord.withSession", type = EntityGraph.EntityGraphType.FETCH)
    @Query("""
            SELECT c FROM ChatRecord c
            WHERE c.user.id = :userId
              AND c.knowledgeBase.id = :kbId
              AND (LOWER(c.question) LIKE LOWER(CONCAT('%', :keyword, '%'))
                   OR LOWER(c.answer) LIKE LOWER(CONCAT('%', :keyword, '%')))
            ORDER BY c.createdAt DESC
            """)
    Page<ChatRecord> searchByUserAndKb(
            @Param("userId") UUID userId,
            @Param("kbId") UUID kbId,
            @Param("keyword") String keyword,
            Pageable pageable);

    long countBySession_Id(UUID sessionId);

    Page<ChatRecord> findByUser_IdAndKnowledgeBase_IdAndSession_IdOrderByCreatedAtDesc(
            UUID userId, UUID kbId, UUID sessionId, Pageable pageable);

    Optional<ChatRecord> findByIdAndUser_Id(UUID id, UUID userId);

    void deleteByUser_IdAndKnowledgeBase_Id(UUID userId, UUID kbId);

    @Query("""
            SELECT c.question FROM ChatRecord c
            WHERE c.user.id = :userId
              AND c.knowledgeBase.id = :kbId
              AND c.question IS NOT NULL
              AND c.question <> ''
            ORDER BY c.createdAt DESC
            """)
    Page<String> findRecentQuestions(
            @Param("userId") UUID userId, @Param("kbId") UUID kbId, Pageable pageable);

    // 新增方法用于推荐和分析
    List<ChatRecord> findByUserIdAndKnowledgeBaseIdOrderByCreatedAtDesc(UUID userId, UUID kbId, Pageable pageable);
    
    List<ChatRecord> findByKnowledgeBaseIdOrderByCreatedAtDesc(UUID kbId, Pageable pageable);
    
    long countByUserIdAndCreatedAtBetween(UUID userId, Instant start, Instant end);
    
    long countByKnowledgeBaseId(UUID kbId);
    
    long countByKnowledgeBaseIdAndCreatedAtAfter(UUID kbId, Instant after);
    
    List<ChatRecord> findByKnowledgeBaseIdAndCreatedAtBetween(UUID kbId, Instant start, Instant end);

    @Query(
            """
            SELECT AVG(c.latencyMs) FROM ChatRecord c
            WHERE c.user.id = :userId
              AND c.createdAt >= :start AND c.createdAt <= :end
              AND c.latencyMs IS NOT NULL""")
    Optional<Double> averageLatencyMsByUserAndCreatedAtBetween(
            @Param("userId") UUID userId, @Param("start") Instant start, @Param("end") Instant end);

    @Query(
            """
            SELECT COUNT(c) FROM ChatRecord c
            WHERE c.user.id = :userId
              AND c.createdAt >= :start AND c.createdAt <= :end
              AND c.helpful IS NOT NULL""")
    long countFeedbackByUserAndCreatedAtBetween(
            @Param("userId") UUID userId, @Param("start") Instant start, @Param("end") Instant end);

    @Query(
            """
            SELECT COUNT(c) FROM ChatRecord c
            WHERE c.user.id = :userId
              AND c.createdAt >= :start AND c.createdAt <= :end
              AND c.helpful = true""")
    long countPositiveFeedbackByUserAndCreatedAtBetween(
            @Param("userId") UUID userId, @Param("start") Instant start, @Param("end") Instant end);
}

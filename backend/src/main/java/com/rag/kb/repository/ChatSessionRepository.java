package com.rag.kb.repository;

import com.rag.kb.domain.ChatSession;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatSessionRepository extends JpaRepository<ChatSession, UUID> {

    Optional<ChatSession> findByIdAndUser_IdAndKnowledgeBase_Id(UUID id, UUID userId, UUID kbId);
}

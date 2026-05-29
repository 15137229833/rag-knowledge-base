package com.rag.kb.repository;

import com.rag.kb.domain.KnowledgeBase;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface KnowledgeBaseRepository extends JpaRepository<KnowledgeBase, UUID> {

    List<KnowledgeBase> findByOwner_Id(UUID ownerId);
}

package com.rag.kb.repository;

import com.rag.kb.domain.KbDocumentComment;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface KbDocumentCommentRepository extends JpaRepository<KbDocumentComment, UUID> {

    List<KbDocumentComment> findByDocument_IdOrderByCreatedAtDesc(UUID documentId);
}

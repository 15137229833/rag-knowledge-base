package com.rag.kb.repository;

import com.rag.kb.domain.KbDocumentVersion;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface KbDocumentVersionRepository extends JpaRepository<KbDocumentVersion, UUID> {

    List<KbDocumentVersion> findByDocument_IdOrderByVersionNumberDesc(UUID documentId);
}

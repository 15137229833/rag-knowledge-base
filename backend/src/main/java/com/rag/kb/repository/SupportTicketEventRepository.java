package com.rag.kb.repository;

import com.rag.kb.domain.SupportTicketEvent;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SupportTicketEventRepository extends JpaRepository<SupportTicketEvent, UUID> {
    List<SupportTicketEvent> findByTicketIdOrderByCreatedAtDesc(UUID ticketId);
}


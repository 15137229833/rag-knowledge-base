package com.rag.kb.repository;

import com.rag.kb.domain.SupportTicket;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SupportTicketRepository extends JpaRepository<SupportTicket, UUID> {
    Page<SupportTicket> findByCreatedByOrderByUpdatedAtDesc(UUID createdBy, Pageable pageable);
}


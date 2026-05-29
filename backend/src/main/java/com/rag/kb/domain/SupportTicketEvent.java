package com.rag.kb.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(name = "support_ticket_event")
@Getter
@Setter
@NoArgsConstructor
public class SupportTicketEvent {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @Column(name = "ticket_id", nullable = false)
    private UUID ticketId;

    @Column(name = "actor_user_id")
    private UUID actorUserId;

    @Column(name = "event_type", nullable = false, length = 32)
    private String eventType;

    @Column(columnDefinition = "TEXT")
    private String message;

    @Column(name = "meta_json", columnDefinition = "TEXT")
    private String metaJson;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}


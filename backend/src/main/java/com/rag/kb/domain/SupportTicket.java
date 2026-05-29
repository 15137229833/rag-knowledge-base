package com.rag.kb.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
@Table(name = "support_ticket")
@Getter
@Setter
@NoArgsConstructor
public class SupportTicket {

    public enum Priority {
        LOW,
        NORMAL,
        HIGH,
        URGENT
    }

    public enum Status {
        OPEN,
        IN_PROGRESS,
        RESOLVED,
        CLOSED
    }

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Column(length = 128)
    private String topic;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String content;

    @Column(length = 128)
    private String contact;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private Status status = Status.OPEN;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Priority priority = Priority.NORMAL;

    @Column(name = "admin_note", columnDefinition = "TEXT")
    private String adminNote;

    /** JSON 数组字符串：[{id,filename,objectKey,contentType,sizeBytes,uploadedAt}] */
    @Column(name = "attachments_json", columnDefinition = "TEXT")
    private String attachmentsJson;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}


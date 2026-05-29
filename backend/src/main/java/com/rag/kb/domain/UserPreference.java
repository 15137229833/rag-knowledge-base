package com.rag.kb.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "user_preference")
@Data
@NoArgsConstructor
public class UserPreference {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @Column(name = "user_id", unique = true, nullable = false)
    private UUID userId;

    @Column(name = "answer_style", length = 20)
    private String answerStyle = "concise";

    @Column(length = 10)
    private String language = "zh";

    @Column(name = "expertise_level", length = 20)
    private String expertiseLevel = "intermediate";

    /** 库中为 TEXT，存 JSON 数组字符串，如 ["产品","合规"] */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "favorite_topics", nullable = false, columnDefinition = "text")
    private List<String> favoriteTopics = new ArrayList<>();

    @Column(name = "preferred_response_length")
    private Integer preferredResponseLength = 500;

    @Column(name = "include_examples")
    private Boolean includeExamples = true;

    @Column(name = "include_references")
    private Boolean includeReferences = true;

    @Column(length = 20)
    private String tone = "professional";

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at")
    private Instant updatedAt = Instant.now();

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }
}

package com.rag.kb.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "app_config")
@Getter
@Setter
@NoArgsConstructor
public class AppConfig {

    @Id
    @Column(name = "config_key", length = 128)
    private String key;

    @Column(name = "config_value", columnDefinition = "TEXT", nullable = false)
    private String value;

    @Column(name = "updated_by")
    private UUID updatedBy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}

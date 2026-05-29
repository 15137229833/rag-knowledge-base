package com.rag.kb.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UuidGenerator;

/**
 * API Token 实体
 */
@Entity
@Table(name = "api_token")
@Data
@NoArgsConstructor
public class ApiToken {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @Column(unique = true, nullable = false, length = 255)
    private String token;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "app_name", length = 255)
    private String appName;

    @Column(name = "app_description", columnDefinition = "TEXT")
    private String appDescription;

    @Column(name = "is_active")
    private Boolean isActive = true;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "last_used_at")
    private Instant lastUsedAt;

    @Column(name = "rate_limit_per_minute")
    private Integer rateLimitPerMinute = 60;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at")
    private Instant updatedAt = Instant.now();

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }

    /**
     * 检查 Token 是否有效
     */
    public boolean isValid() {
        if (!isActive) {
            return false;
        }
        if (expiresAt != null && Instant.now().isAfter(expiresAt)) {
            return false;
        }
        return true;
    }

    /**
     * 检查是否需要更新最后使用时间
     */
    public boolean shouldUpdateLastUsed() {
        if (lastUsedAt == null) {
            return true;
        }
        // 如果超过5分钟未使用，则更新
        return Instant.now().minusSeconds(300).isAfter(lastUsedAt);
    }
}

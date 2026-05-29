package com.rag.kb.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

public final class ApiTokenDtos {

    private ApiTokenDtos() {}

    public record CreateTokenRequest(
            @NotBlank @Size(max = 255) String name,
            @Size(max = 255) String appName,
            @Size(max = 2000) String appDescription,
            Integer expiryDays) {}

    public record CreateTokenResponse(
            UUID id,
            String token,
            String name,
            String appName,
            Instant expiresAt) {}

    public record TokenListItem(
            UUID id,
            String name,
            String tokenMasked,
            String appName,
            boolean active,
            Instant expiresAt,
            Instant lastUsedAt,
            Integer rateLimitPerMinute,
            Instant createdAt) {}
}

package com.rag.kb.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

public final class KnowledgeBaseDtos {

    private KnowledgeBaseDtos() {}

    public record CreateKbRequest(
            @NotBlank @Size(max = 128) String name, @Size(max = 2000) String description) {}

    public record UpdateKbRequest(
            @NotBlank @Size(max = 128) String name, @Size(max = 2000) String description) {}

    public record KbResponse(
            UUID id,
            String name,
            String description,
            UUID ownerUserId,
            String role,
            long documentCount,
            Instant lastActivityAt) {}

    public record AddMemberRequest(@NotBlank String username, String permission) {}

    /** 知识库成员（不含所有者；所有者单独展示） */
    public record KbMemberResponse(UUID userId, String username, String permission) {}
}

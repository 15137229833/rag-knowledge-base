package com.rag.kb.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

public final class CollaborationDtos {

    private CollaborationDtos() {}

    public record CommentResponse(
            UUID id,
            UUID documentId,
            UUID userId,
            String username,
            String content,
            Integer position,
            UUID parentCommentId,
            Instant createdAt) {}

    public record CreateCommentRequest(
            @NotBlank @Size(max = 4000) String content,
            Integer position,
            UUID parentCommentId) {}

    public record VersionResponse(
            UUID id,
            UUID documentId,
            int versionNumber,
            String contentHash,
            String changeLog,
            UUID createdBy,
            String createdByUsername,
            Instant createdAt) {}

    public record CreateVersionRequest(
            @NotBlank @Size(max = 64) String contentHash,
            @Size(max = 2000) String changeLog) {}
}

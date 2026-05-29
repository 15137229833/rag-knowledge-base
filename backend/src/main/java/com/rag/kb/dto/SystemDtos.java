package com.rag.kb.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class SystemDtos {

    private SystemDtos() {}

    public record ModelSettingsResponse(
            String provider,
            String chatModel,
            String embeddingModel,
            String apiBaseUrl,
            String apiKeyMasked,
            Double defaultTemperature,
            Double defaultTopP,
            Integer defaultTopK,
            String vectorDbType,
            String vectorDbEndpoint,
            Instant updatedAt,
            UUID updatedBy) {}

    public record SaveModelSettingsRequest(
            @NotBlank String provider,
            @NotBlank String chatModel,
            @NotBlank String embeddingModel,
            String apiBaseUrl,
            String apiKey,
            @NotNull Double defaultTemperature,
            @NotNull Double defaultTopP,
            @NotNull Integer defaultTopK,
            @NotBlank String vectorDbType,
            String vectorDbEndpoint) {}

    public record PromptTemplateCreateRequest(
            @NotBlank String name, String description, @NotBlank String templateText, Boolean enabled) {}

    public record PromptTemplateUpdateRequest(
            @NotBlank String name, String description, @NotBlank String templateText, Boolean enabled) {}

    public record PromptTemplateItem(
            UUID id,
            String name,
            String description,
            String templateText,
            boolean enabled,
            Instant createdAt,
            Instant updatedAt,
            UUID createdBy) {}

    public record PromptTemplateRenderRequest(@NotBlank String templateText, Map<String, String> variables) {}

    public record PromptTemplateRenderResponse(String result) {}

    public record ServiceHealth(String name, boolean up, String message) {}

    public record RuntimeSummaryResponse(
            String llmProvider,
            String chatBackend,
            String chatModel,
            String baseUrl,
            String visionProvider,
            String openAiVisionModel,
            String ollamaVisionModel,
            boolean imageCaptionEnabled,
            boolean videoCaptionEnabled,
            String retrievalMode,
            String chunkTable) {}

    public record SystemStatusResponse(
            Instant serverTime,
            long uptimeSeconds,
            List<ServiceHealth> services,
            long totalUsers,
            long totalKnowledgeBases,
            long totalDocuments,
            long totalPrompts,
            long chatCalls24h,
            long uploadCalls24h,
            long errorCalls24h) {}

    public record DocumentCenterItem(
            UUID id,
            UUID knowledgeBaseId,
            String knowledgeBaseName,
            String filename,
            String contentType,
            Long sizeBytes,
            String status,
            String errorMessage,
            Instant createdAt,
            List<String> tags,
            String sourceUrl) {}

    public record SupportTicketCreateRequest(String topic, @NotBlank String content, String contact, String priority) {}

    public record SupportTicketUpdateRequest(@NotNull String status, String adminNote) {}

    public record SupportAttachmentItem(
            UUID id,
            String filename,
            String objectKey,
            String contentType,
            Long sizeBytes,
            Instant uploadedAt) {}

    public record SupportTicketEventItem(
            UUID id, UUID actorUserId, String eventType, String message, Instant createdAt) {}

    public record SupportTicketItem(
            UUID id,
            UUID createdBy,
            String topic,
            String content,
            String contact,
            String status,
            String priority,
            String adminNote,
            List<SupportAttachmentItem> attachments,
            Instant createdAt,
            Instant updatedAt) {}

    public record SupportTicketDetail(
            SupportTicketItem ticket,
            List<SupportTicketEventItem> events) {}
}

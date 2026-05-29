package com.rag.kb.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class ChatDtos {

    private ChatDtos() {}

    public record ChatRequest(
            @NotNull UUID knowledgeBaseId,
            @NotBlank String question,
            Integer contextChunks,
            Double temperature,
            Double topP,
            Integer topK,
            String answerStyle,
            UUID sessionId,
            Boolean newSession) {}

    public record CitationDto(
            String vectorDocId,
            UUID documentId,
            String documentName,
            int chunkIndex,
            Integer pageNo,
            String excerpt,
            String sourceUrl,
            Integer lineStart,
            Integer lineEnd,
            /** text | image；旧数据缺省为 null，前端按文本处理 */
            String modality) {}

    public record ChatResponse(
            String answer,
            List<CitationDto> citations,
            int retrievedCandidates,
            int contextChunksUsed,
            long latencyMs,
            UUID sessionId,
            String sessionTitle) {}

    public record ChatHistoryItem(
            UUID id,
            UUID knowledgeBaseId,
            UUID sessionId,
            String sessionTitle,
            String question,
            String answer,
            List<CitationDto> citations,
            Boolean helpful,
            String feedbackNote,
            Instant createdAt) {}

    public record ChatFeedbackRequest(Boolean helpful, String note) {}

    public record SuggestQuestionsResponse(List<String> suggestions) {}

    /** SSE 结束帧：聚合元数据与历史记录 id（用于当前回答点赞/点踩） */
    public record ChatStreamDone(
            String answer,
            List<CitationDto> citations,
            int retrievedCandidates,
            int contextChunksUsed,
            long latencyMs,
            UUID historyId,
            UUID sessionId,
            String sessionTitle) {}
}

package com.rag.kb.dto;

import com.rag.kb.domain.DocumentStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class DocumentDtos {

    private DocumentDtos() {}

    public record DocumentResponse(
            UUID id,
            UUID knowledgeBaseId,
            String filename,
            String contentType,
            Long sizeBytes,
            DocumentStatus status,
            String errorMessage,
            Instant createdAt,
            List<String> tags,
            String sourceUrl) {}

    /**
     * @param scrapeText        是否抓取正文文本，默认 true（null 视为 true）
     * @param scrapeImages      是否抓取页面图片并分别入库
     * @param scrapeVideoLinks  是否摘录 video/iframe/常见视频站链接写入文本附录
     * @param maxImages         图片数量上限，默认 20；非法值在服务端钳制到 1–50（避免前端传 0 导致 400）
     */
    public record ImportUrlRequest(
            @NotBlank String url,
            boolean overwrite,
            Boolean scrapeText,
            Boolean scrapeImages,
            Boolean scrapeVideoLinks,
            Integer maxImages) {}

    public record BatchDeleteRequest(@NotEmpty List<UUID> ids) {}

    public record UpdateTagsRequest(List<String> tags) {}
}

package com.rag.kb.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rag.kb.config.MultimodalProperties;
import com.rag.kb.config.RagRetrievalProperties;
import com.rag.kb.domain.DocumentStatus;
import com.rag.kb.domain.KbDocument;
import com.rag.kb.rag.TextChunkStoreRepository;
import com.rag.kb.repository.KbDocumentRepository;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class IngestionService {

    private final KbDocumentRepository kbDocumentRepository;
    private final TextChunkStoreRepository textChunkStoreRepository;
    private final StorageService storageService;
    private final ObjectMapper objectMapper;
    private final RagRetrievalProperties ragRetrievalProperties;
    private final MultimodalProperties multimodalProperties;
    private final OllamaVisionCaptionService ollamaVisionCaptionService;
    private final VideoFrameExtractorService videoFrameExtractorService;

    @Transactional
    public void ingest(UUID documentId) {
        KbDocument doc = kbDocumentRepository.findById(documentId).orElse(null);
        if (doc == null) {
            return;
        }
        UUID kbId = doc.getKnowledgeBase().getId();
        String filename = doc.getFilename();
        try {
            doc.setStatus(DocumentStatus.PROCESSING);
            doc.setErrorMessage(null);
            kbDocumentRepository.save(doc);

            deleteExistingChunks(doc);

            byte[] bytes = storageService.getObjectBytes(doc.getStorageObjectKey());
            final List<TextExtractors.PageText> pages;
            if (TextExtractors.isImageFile(filename, doc.getContentType())) {
                pages = List.of(new TextExtractors.PageText(null, buildImageBody(bytes, filename)));
            } else if (TextExtractors.isVideoFile(filename, doc.getContentType())) {
                pages = List.of(new TextExtractors.PageText(null, buildVideoBody(bytes, filename)));
            } else {
                pages = TextExtractors.extractPages(bytes, filename, doc.getContentType());
            }
            List<Document> chunkDocs = buildChunkDocuments(doc, kbId, pages);
            if (chunkDocs.isEmpty()) {
                throw new IllegalStateException("未解析到可用文本");
            }

            textChunkStoreRepository.saveAll(chunkDocs);

            List<String> ids = chunkDocs.stream().map(Document::getId).toList();
            doc.setVectorDocIdsJson(objectMapper.writeValueAsString(ids));
            doc.setStatus(DocumentStatus.READY);
            kbDocumentRepository.save(doc);
        } catch (OutOfMemoryError oom) {
            log.error("文档入库 OOM documentId={}", documentId, oom);
            doc.setStatus(DocumentStatus.FAILED);
            doc.setErrorMessage("内存不足，请尝试处理更小的文件或增加 JVM 内存");
            kbDocumentRepository.save(doc);
        } catch (Exception e) {
            log.error("文档入库失败 documentId={}, type={}",
                documentId, e.getClass().getSimpleName(), e);

            doc.setStatus(DocumentStatus.FAILED);

            String errorMsg;
            boolean retryable = true;

            if (e instanceof java.io.IOException || e instanceof java.net.SocketTimeoutException) {
                errorMsg = "网络或存储错误，可重试: " + (e.getMessage() != null ? e.getMessage() : "连接失败");
            } else if (e instanceof org.springframework.web.client.RestClientException) {
                errorMsg = "AI 服务连接失败，请检查 Qwen / DashScope 配置: " +
                    (e.getMessage() != null ? e.getMessage() : "连接失败");
            } else if (e instanceof IllegalStateException && e.getMessage() != null &&
                       e.getMessage().contains("未解析到可用文本")) {
                errorMsg = "文档解析失败：文件可能为空、格式不支持，或视频未抽取到有效关键帧";
                retryable = false;
            } else {
                errorMsg = "未知错误: " + e.getClass().getSimpleName() +
                    (e.getMessage() != null ? " - " + truncate(e.getMessage(), 200) : "");
            }

            doc.setErrorMessage(errorMsg);
            kbDocumentRepository.save(doc);

            log.warn("文档入库失败详情 documentId={}, retryable={}, error={}",
                documentId, retryable, errorMsg);
        }
    }

    private String buildImageBody(byte[] bytes, String filename) {
        String caption;
        if (!multimodalProperties.isImageCaptionEnabled()) {
            caption = "（已关闭视觉描述，仅可凭文件名与占位说明检索；可在配置中开启 app.multimodal.image-caption-enabled）";
        } else {
            caption = ollamaVisionCaptionService.captionImage(bytes, filename);
        }
        if (caption == null || caption.isBlank()) {
            if (multimodalProperties.isCaptionFallbackOnError()) {
                caption = imageCaptionFallback(filename);
            } else {
                throw new IllegalStateException(
                        "图片描述失败：请配置 Qwen/OpenAI 兼容多模态模型（app.multimodal.open-ai-vision-model）"
                                + "，也可开启 caption-fallback-on-error");
            }
        }
        return "【图片文档】文件名：" + filename + "\n视觉描述：\n" + caption;
    }

    private String buildVideoBody(byte[] bytes, String filename) {
        if (!multimodalProperties.isVideoCaptionEnabled()) {
            return "【视频文档】文件名：" + filename + "\n（已关闭视频视觉摘要，仅按文件名占位入库）";
        }
        List<OllamaVisionCaptionService.VideoFrame> frames = videoFrameExtractorService.extractFrames(bytes, filename);
        if (frames.isEmpty()) {
            if (multimodalProperties.isCaptionFallbackOnError()) {
                return videoCaptionFallback(filename);
            }
            throw new IllegalStateException("未抽取到视频关键帧");
        }
        String caption = ollamaVisionCaptionService.captionVideoFrames(frames, filename);
        if (caption == null || caption.isBlank()) {
            if (multimodalProperties.isCaptionFallbackOnError()) {
                return videoCaptionFallback(filename);
            }
            throw new IllegalStateException("视频多模态摘要失败");
        }
        return "【视频文档】文件名：" + filename
                + "\n关键帧数：" + frames.size()
                + "\n视频摘要：\n" + caption;
    }

    private static String imageCaptionFallback(String filename) {
        return "（视觉描述暂不可用）已上传图片「"
                + filename
                + "」。请配置 Qwen / DashScope 多模态模型并设置 app.multimodal.open-ai-vision-model；"
                + "也可在 application.yml 中设置 app.multimodal.image-caption-enabled=false 以仅用语义占位入库。";
    }

    private static String videoCaptionFallback(String filename) {
        return "【视频文档】文件名：" + filename
                + "\n（视频视觉摘要暂不可用）请确认服务器已安装 ffmpeg，且已配置 Qwen 多模态模型。"
                + " 也可先关闭 app.multimodal.video-caption-enabled，以文件名占位方式入库。";
    }

    private void deleteExistingChunks(KbDocument doc) throws java.io.IOException {
        if (doc.getVectorDocIdsJson() == null || doc.getVectorDocIdsJson().isBlank()) {
            return;
        }
        List<String> old = objectMapper.readValue(doc.getVectorDocIdsJson(), new TypeReference<List<String>>() {});
        if (old != null && !old.isEmpty()) {
            textChunkStoreRepository.deleteByIds(old);
        }
        doc.setVectorDocIdsJson(null);
    }

    private List<Document> buildChunkDocuments(KbDocument doc, UUID kbId, List<TextExtractors.PageText> pages) {
        UUID documentId = doc.getId();
        String filename = doc.getFilename();
        String sourceUrl = doc.getSourceUrl();
        List<Document> out = new ArrayList<>();
        int globalIndex = 0;
        for (TextExtractors.PageText page : pages) {
            String pageText = page.getText();
            List<String> parts = buildChunksForPage(pageText);
            int searchFrom = 0;
            for (String chunk : parts) {
                if (chunk.isBlank()) {
                    continue;
                }
                String id = UUID.randomUUID().toString();
                Map<String, Object> meta = new HashMap<>();
                meta.put("kbId", kbId.toString());
                meta.put("documentId", documentId.toString());
                meta.put("filename", filename);
                meta.put("chunkIndex", String.valueOf(globalIndex));
                meta.put("pageNo", page.getPageNo() == null ? "" : String.valueOf(page.getPageNo()));
                int[] lr = lineRangeForChunk(pageText, chunk, searchFrom);
                meta.put("lineStart", String.valueOf(lr[0]));
                meta.put("lineEnd", String.valueOf(lr[1]));
                searchFrom = lr[2];
                if (TextExtractors.isImageFile(filename, doc.getContentType())) {
                    meta.put("modality", "image");
                } else if (TextExtractors.isVideoFile(filename, doc.getContentType())) {
                    meta.put("modality", "video");
                } else {
                    meta.put("modality", "text");
                }
                if (sourceUrl != null && !sourceUrl.isBlank()) {
                    meta.put("sourceUrl", sourceUrl.strip());
                }
                out.add(new Document(id, chunk, meta));
                globalIndex++;
            }
        }
        return out;
    }

    private static int lineNumberAtIndex(String text, int index) {
        int line = 1;
        int len = Math.min(index, text.length());
        for (int i = 0; i < len; i++) {
            if (text.charAt(i) == '\n') {
                line++;
            }
        }
        return line;
    }

    private static int newlineCountIn(String s) {
        int n = 0;
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) == '\n') {
                n++;
            }
        }
        return n;
    }

    private static int[] lineRangeForChunk(String pageText, String chunk, int searchFrom) {
        if (chunk.isBlank()) {
            return new int[] {1, 1, searchFrom};
        }
        String prefix = chunk.length() <= 80 ? chunk : chunk.substring(0, 80);
        int idx = pageText.indexOf(prefix, Math.max(0, searchFrom));
        if (idx < 0) {
            idx = pageText.indexOf(prefix);
        }
        if (idx < 0) {
            idx = Math.max(0, Math.min(searchFrom, pageText.length()));
        }
        int lineStart = lineNumberAtIndex(pageText, idx);
        int lineEnd = lineStart + newlineCountIn(chunk);
        int nextFrom = idx + Math.max(1, chunk.length() / 4);
        return new int[] {lineStart, lineEnd, nextFrom};
    }

    private List<String> buildChunksForPage(String pageText) {
        var ing = ragRetrievalProperties.getIngest();
        if (ing.isSemanticChunking()) {
            return TextChunker.chunkSemantic(
                    pageText,
                    ing.getSemanticMaxChunk(),
                    ing.getSemanticOverlap(),
                    ing.getSemanticMinChunk());
        }
        return TextChunker.chunk(pageText, TextChunker.DEFAULT_CHUNK_SIZE, TextChunker.DEFAULT_OVERLAP);
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max);
    }
}

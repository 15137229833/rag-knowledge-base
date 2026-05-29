package com.rag.kb.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rag.kb.domain.DocumentStatus;
import com.rag.kb.domain.KbDocument;
import com.rag.kb.domain.KnowledgeBase;
import com.rag.kb.dto.DocumentDtos.BatchDeleteRequest;
import com.rag.kb.dto.DocumentDtos.DocumentResponse;
import com.rag.kb.dto.DocumentDtos.ImportUrlRequest;
import com.rag.kb.dto.DocumentDtos.UpdateTagsRequest;
import com.rag.kb.repository.KbDocumentRepository;
import com.rag.kb.rag.TextChunkStoreRepository;
import com.rag.kb.scrape.HttpBinaryFetch;
import com.rag.kb.scrape.UrlImportScraper;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jsoup.Jsoup;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriUtils;

@Service
@RequiredArgsConstructor
public class KbDocumentService {

    private static final String SCRAPER_UA = "RagKbScraper/1.1";
    private static final long MAX_SCRAPED_IMAGE_BYTES = 12L * 1024 * 1024;

    private final KbDocumentRepository kbDocumentRepository;
    private final KnowledgeBaseService knowledgeBaseService;
    private final StorageService storageService;
    private final DocumentIngestionRunner documentIngestionRunner;
    private final TextChunkStoreRepository textChunkStoreRepository;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public List<DocumentResponse> list(
            UUID kbId,
            UUID userId,
            String status,
            String ext,
            String tag,
            String sort) {
        knowledgeBaseService.requireReadable(kbId, userId);
        List<KbDocument> rows = kbDocumentRepository.findByKnowledgeBase_IdOrderByCreatedAtDesc(kbId);
        List<KbDocument> filtered = new ArrayList<>();
        for (KbDocument d : rows) {
            if (!matchStatus(d, status)) {
                continue;
            }
            if (!matchExtension(d, ext)) {
                continue;
            }
            if (!matchTag(d, tag)) {
                continue;
            }
            filtered.add(d);
        }
        Comparator<KbDocument> cmp = Comparator.comparing(KbDocument::getCreatedAt);
        if (sort != null && sort.equalsIgnoreCase("ASC")) {
            filtered.sort(cmp);
        } else {
            filtered.sort(cmp.reversed());
        }
        return filtered.stream().map(this::toResponse).toList();
    }

    private static boolean matchStatus(KbDocument d, String status) {
        if (status == null || status.isBlank() || "ALL".equalsIgnoreCase(status.trim())) {
            return true;
        }
        try {
            DocumentStatus st = DocumentStatus.valueOf(status.trim().toUpperCase(Locale.ROOT));
            return d.getStatus() == st;
        } catch (Exception e) {
            return true;
        }
    }

    private static boolean matchExtension(KbDocument d, String ext) {
        if (ext == null || ext.isBlank()) {
            return true;
        }
        String e = ext.trim().toLowerCase(Locale.ROOT);
        if (!e.startsWith(".")) {
            e = "." + e;
        }
        String name = d.getFilename() == null ? "" : d.getFilename().toLowerCase(Locale.ROOT);
        return name.endsWith(e);
    }

    private boolean matchTag(KbDocument d, String tag) {
        if (tag == null || tag.isBlank()) {
            return true;
        }
        String t = tag.trim().toLowerCase(Locale.ROOT);
        for (String x : readTagList(d)) {
            if (x != null && x.toLowerCase(Locale.ROOT).contains(t)) {
                return true;
            }
        }
        return false;
    }

    @Transactional
    public DocumentResponse upload(UUID kbId, UUID userId, MultipartFile file, boolean overwriteDuplicate) {
        knowledgeBaseService.requireWritable(kbId, userId);
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "文件为空");
        }
        KnowledgeBase kb = knowledgeBaseService.requireReadable(kbId, userId);
        String original = file.getOriginalFilename() != null ? file.getOriginalFilename() : "upload.bin";
        validateExtension(original);
        validateContentType(original, file.getContentType());

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "读取文件失败");
        }
        return persistBinaryDocument(
                kb,
                kbId,
                userId,
                original,
                file.getContentType(),
                bytes,
                null,
                overwriteDuplicate,
                "DOC_UPLOAD",
                original);
    }

    @Transactional
    public List<DocumentResponse> importFromUrl(UUID kbId, UUID userId, ImportUrlRequest req) {
        knowledgeBaseService.requireWritable(kbId, userId);
        KnowledgeBase kb = knowledgeBaseService.requireReadable(kbId, userId);
        String url = req.url() == null ? "" : req.url().trim();
        assertHttpUrl(url);

        boolean scrapeText = resolveScrapeText(req);
        boolean scrapeImages = Boolean.TRUE.equals(req.scrapeImages());
        boolean scrapeVideos = Boolean.TRUE.equals(req.scrapeVideoLinks());
        int maxImages = req.maxImages() == null ? 20 : Math.min(50, Math.max(1, req.maxImages()));

        if (!scrapeText && !scrapeImages && !scrapeVideos) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请至少选择一种抓取内容（正文 / 图片 / 视频链接）");
        }

        org.jsoup.nodes.Document html;
        try {
            html = Jsoup.connect(url)
                    .timeout(25_000)
                    .userAgent(SCRAPER_UA)
                    .followRedirects(true)
                    .get();
        } catch (Exception e) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "网页抓取失败：" + truncateErr(e.getMessage(), 240));
        }

        String title = html.title();
        if (title == null || title.isBlank()) {
            title = "web-import";
        }
        String safeTitle = slugifyFilename(title);

        String videoSection = scrapeVideos ? UrlImportScraper.buildVideoLinksSection(html, url).trim() : "";

        String bodyText = "";
        if (scrapeText) {
            bodyText = UrlImportScraper.extractCleanText(html).trim();
        }

        StringBuilder textBuilder = new StringBuilder();
        if (scrapeText) {
            if (bodyText.isBlank()) {
                if (scrapeVideos && !videoSection.isBlank()) {
                    textBuilder.append("（本页未解析到可见正文文本）\n");
                } else if (!scrapeImages) {
                    throw new ResponseStatusException(
                            HttpStatus.BAD_REQUEST, "页面未提取到有效正文（可能为纯脚本页或需登录）");
                }
            } else if (bodyText.length() > 1_500_000) {
                textBuilder.append(bodyText, 0, 1_500_000).append("\n\n...(truncated)");
            } else {
                textBuilder.append(bodyText);
            }
        }
        if (scrapeVideos) {
            if (videoSection.isBlank()) {
                if (!scrapeText && !scrapeImages) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "未检测到视频或嵌入链接");
                }
            } else {
                if (textBuilder.length() > 0) {
                    textBuilder.append("\n\n---\n");
                }
                textBuilder.append("# 视频与嵌入链接（自动摘录）\n来源：")
                        .append(url)
                        .append("\n\n")
                        .append(videoSection);
            }
        }

        List<DocumentResponse> created = new ArrayList<>();
        if (textBuilder.length() > 0) {
            byte[] textBytes = textBuilder.toString().getBytes(StandardCharsets.UTF_8);
            String textFilename = safeTitle + ".txt";
            created.add(
                    persistBinaryDocument(
                            kb,
                            kbId,
                            userId,
                            textFilename,
                            "text/plain; charset=utf-8",
                            textBytes,
                            url,
                            req.overwrite(),
                            "DOC_URL_IMPORT",
                            url));
        }

        if (scrapeImages) {
            List<String> imageUrls = UrlImportScraper.collectImageUrls(html, url, maxImages);
            if (imageUrls.isEmpty() && created.isEmpty()) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "未发现可抓取的图片，且未生成文本索引（请调整选项或更换页面）");
            }
            int ok = 0;
            for (int i = 0; i < imageUrls.size(); i++) {
                String imgUrl = imageUrls.get(i);
                try {
                    HttpBinaryFetch.Result r = HttpBinaryFetch.get(imgUrl, MAX_SCRAPED_IMAGE_BYTES, SCRAPER_UA);
                    String ext = pickImageExtension(imgUrl, r.contentType());
                    if (ext.isEmpty()) {
                        continue;
                    }
                    String fn = safeTitle + "-web-img-" + (i + 1) + ext;
                    String ct = mapImageContentType(ext);
                    created.add(
                            persistBinaryDocument(
                                    kb,
                                    kbId,
                                    userId,
                                    fn,
                                    ct,
                                    r.body(),
                                    url,
                                    req.overwrite(),
                                    "DOC_URL_IMPORT",
                                    imgUrl));
                    ok++;
                } catch (Exception ignored) {
                    // 单张失败跳过，继续其余图片
                }
            }
            if (ok == 0 && created.isEmpty()) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "图片均未下载成功，且未生成文本索引（可尝试仅抓正文或勾选覆盖重复）");
            }
        }

        if (created.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "未生成任何文档，请调整抓取选项");
        }
        return created;
    }

    /**
     * 将字节写入 MinIO、落库并异步入库向量（与 multipart 上传共用去重逻辑）。
     */
    private DocumentResponse persistBinaryDocument(
            KnowledgeBase kb,
            UUID kbId,
            UUID userId,
            String filename,
            String contentType,
            byte[] bytes,
            String sourceUrl,
            boolean overwrite,
            String auditAction,
            String auditDetail) {
        validateExtension(filename);
        validateContentType(filename, contentType);

        String contentHash = sha256(bytes);
        KbDocument duplicated =
                kbDocumentRepository.findByKnowledgeBase_IdAndContentHash(kbId, contentHash).orElse(null);
        if (duplicated != null) {
            if (!overwrite) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT, "检测到重复文档（内容哈希相同），请启用「覆盖重复」或更换资源：" + filename);
            }
            deleteDocumentData(duplicated);
            kbDocumentRepository.delete(duplicated);
        }

        UUID docId = UUID.randomUUID();
        String safeName = filename.replaceAll("[\\\\/:*?\"<>|]", "_");
        String objectKey = kbId + "/" + docId + "/" + safeName;
        storageService.putObject(objectKey, bytes, contentType);

        KbDocument doc = new KbDocument();
        doc.setKnowledgeBase(kb);
        doc.setFilename(filename);
        doc.setContentType(contentType);
        doc.setSizeBytes((long) bytes.length);
        doc.setStorageObjectKey(objectKey);
        doc.setContentHash(contentHash);
        doc.setStatus(DocumentStatus.PENDING);
        doc.setTags("[]");
        doc.setSourceUrl(sourceUrl);
        doc = kbDocumentRepository.save(doc);

        documentIngestionRunner.scheduleAfterCommit(doc.getId());
        auditService.logByUserId(userId, auditAction, "DOCUMENT", doc.getId().toString(), auditDetail);
        return toResponse(doc);
    }

    /**
     * 是否抓取正文：请求里显式给出则以其为准。
     * 未传 scrapeText（null）时：若已勾选图片或视频链接，则不再默认抓正文，避免出现「只抓图仍生成 txt」。
     * 三者均未扩展时默认 true，兼容只传 url + overwrite 的旧调用。
     */
    private static boolean resolveScrapeText(ImportUrlRequest req) {
        if (req.scrapeText() != null) {
            return req.scrapeText();
        }
        if (Boolean.TRUE.equals(req.scrapeImages()) || Boolean.TRUE.equals(req.scrapeVideoLinks())) {
            return false;
        }
        return true;
    }

    private static String pickImageExtension(String imageUrl, String primaryMime) {
        String m = primaryMime == null ? "" : primaryMime.toLowerCase(Locale.ROOT);
        if (m.contains("png")) {
            return ".png";
        }
        if (m.contains("jpeg")) {
            return ".jpg";
        }
        if (m.contains("image/jpg")) {
            return ".jpg";
        }
        if (m.contains("webp")) {
            return ".webp";
        }
        if (m.contains("gif")) {
            return ".gif";
        }
        String u = imageUrl.toLowerCase(Locale.ROOT);
        int q = u.indexOf('?');
        if (q > 0) {
            u = u.substring(0, q);
        }
        if (u.endsWith(".png")) {
            return ".png";
        }
        if (u.endsWith(".jpg") || u.endsWith(".jpeg")) {
            return ".jpg";
        }
        if (u.endsWith(".webp")) {
            return ".webp";
        }
        if (u.endsWith(".gif")) {
            return ".gif";
        }
        return "";
    }

    private static String mapImageContentType(String ext) {
        return switch (ext.toLowerCase(Locale.ROOT)) {
            case ".png" -> "image/png";
            case ".jpg", ".jpeg" -> "image/jpeg";
            case ".webp" -> "image/webp";
            case ".gif" -> "image/gif";
            default -> "application/octet-stream";
        };
    }

    @Transactional
    public DocumentResponse updateTags(UUID documentId, UUID userId, UpdateTagsRequest req) {
        KbDocument doc = kbDocumentRepository
                .findById(documentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "文档不存在"));
        UUID kbId = doc.getKnowledgeBase().getId();
        knowledgeBaseService.requireWritable(kbId, userId);
        doc.setTags(writeTags(req == null ? List.of() : req.tags()));
        return toResponse(kbDocumentRepository.save(doc));
    }

    @Transactional
    public void batchDelete(UUID userId, BatchDeleteRequest req) {
        LinkedHashSet<UUID> ids = new LinkedHashSet<>(req.ids());
        for (UUID id : ids) {
            delete(id, userId);
        }
    }

    @Transactional(readOnly = true)
    public ResponseEntity<byte[]> downloadFile(UUID documentId, UUID userId) {
        KbDocument doc = kbDocumentRepository
                .findById(documentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "文档不存在"));
        UUID kbId = doc.getKnowledgeBase().getId();
        knowledgeBaseService.requireReadable(kbId, userId);

        byte[] bytes = storageService.getObjectBytes(doc.getStorageObjectKey());
        String ct = doc.getContentType() != null && !doc.getContentType().isBlank()
                ? doc.getContentType()
                : "application/octet-stream";
        MediaType mediaType;
        try {
            mediaType = MediaType.parseMediaType(ct);
        } catch (Exception e) {
            mediaType = MediaType.APPLICATION_OCTET_STREAM;
        }
        String filename = doc.getFilename() != null ? doc.getFilename() : "download";
        String encoded = UriUtils.encode(filename, StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename*=UTF-8''" + encoded)
                .contentType(mediaType)
                .body(bytes);
    }

    @Transactional
    public void delete(UUID documentId, UUID userId) {
        KbDocument doc = kbDocumentRepository
                .findById(documentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "文档不存在"));
        UUID kbId = doc.getKnowledgeBase().getId();
        knowledgeBaseService.requireWritable(kbId, userId);

        deleteDocumentData(doc);
        kbDocumentRepository.delete(doc);
        auditService.logByUserId(userId, "DOC_DELETE", "DOCUMENT", documentId.toString(), doc.getFilename());
    }

    @Transactional
    public DocumentResponse reindex(UUID documentId, UUID userId) {
        KbDocument doc = kbDocumentRepository
                .findById(documentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "文档不存在"));
        UUID kbId = doc.getKnowledgeBase().getId();
        knowledgeBaseService.requireWritable(kbId, userId);

        doc.setStatus(DocumentStatus.PENDING);
        doc.setErrorMessage(null);
        doc = kbDocumentRepository.save(doc);

        documentIngestionRunner.scheduleAfterCommit(doc.getId());
        auditService.logByUserId(userId, "DOC_REINDEX", "DOCUMENT", doc.getId().toString(), doc.getFilename());
        return toResponse(doc);
    }

    private DocumentResponse toResponse(KbDocument d) {
        return new DocumentResponse(
                d.getId(),
                d.getKnowledgeBase().getId(),
                d.getFilename(),
                d.getContentType(),
                d.getSizeBytes(),
                d.getStatus(),
                d.getErrorMessage(),
                d.getCreatedAt(),
                List.copyOf(readTagList(d)),
                d.getSourceUrl());
    }

    private List<String> readTagList(KbDocument d) {
        String raw = d.getTags();
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        try {
            List<String> list = objectMapper.readValue(raw, new TypeReference<List<String>>() {});
            return list == null ? List.of() : list;
        } catch (Exception e) {
            return List.of();
        }
    }

    private String writeTags(List<String> tags) {
        LinkedHashSet<String> set = new LinkedHashSet<>();
        if (tags != null) {
            for (String t : tags) {
                if (t == null) {
                    continue;
                }
                String x = t.trim();
                if (!x.isBlank() && x.length() <= 32) {
                    set.add(x);
                }
                if (set.size() >= 20) {
                    break;
                }
            }
        }
        try {
            return objectMapper.writeValueAsString(set.stream().toList());
        } catch (Exception e) {
            return "[]";
        }
    }

    private void deleteDocumentData(KbDocument doc) {
        try {
            if (doc.getVectorDocIdsJson() != null && !doc.getVectorDocIdsJson().isBlank()) {
                List<String> ids = objectMapper.readValue(doc.getVectorDocIdsJson(), new TypeReference<List<String>>() {});
                textChunkStoreRepository.deleteByIds(ids);
                doc.setVectorDocIdsJson(null);
            }
        } catch (Exception ignored) {
        }
        try {
            storageService.removeObject(doc.getStorageObjectKey());
        } catch (Exception ignored) {
        }
    }

    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
        "application/pdf",
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "text/markdown",
        "text/plain",
        "image/png",
        "image/jpeg",
        "image/webp",
        "image/gif",
        "video/mp4",
        "video/quicktime",
        "video/x-m4v",
        "video/webm",
        "video/x-msvideo"
    );

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
        ".pdf",
        ".docx",
        ".md",
        ".txt",
        ".markdown",
        ".png",
        ".jpg",
        ".jpeg",
        ".webp",
        ".gif",
        ".mp4",
        ".mov",
        ".m4v",
        ".webm",
        ".avi");

    private static void validateExtension(String filename) {
        if (filename == null || filename.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "文件名不能为空");
        }
        
        String lower = filename.toLowerCase(Locale.ROOT);
        boolean hasValidExtension = ALLOWED_EXTENSIONS.stream()
            .anyMatch(lower::endsWith);
        
        if (!hasValidExtension) {
            throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                "仅支持以下文件类型: " + String.join(", ", ALLOWED_EXTENSIONS));
        }
    }
    
    private void validateContentType(String filename, String contentType) {
        if (contentType == null || contentType.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "无法检测文件类型");
        }
        String lower = filename.toLowerCase(Locale.ROOT);
        String primary = primaryMimeType(contentType);

        if (isImageExtension(lower)) {
            if (primary.startsWith("image/") || "application/octet-stream".equals(primary)) {
                return;
            }
            throw new ResponseStatusException(
                    HttpStatus.UNSUPPORTED_MEDIA_TYPE, "图片上传需使用 image/* 或 application/octet-stream");
        }

        if (isVideoExtension(lower)) {
            if (primary.startsWith("video/") || "application/octet-stream".equals(primary)) {
                return;
            }
            throw new ResponseStatusException(
                    HttpStatus.UNSUPPORTED_MEDIA_TYPE, "视频上传需使用 video/* 或 application/octet-stream");
        }

        if (!ALLOWED_CONTENT_TYPES.contains(primary)) {
            throw new ResponseStatusException(
                    HttpStatus.UNSUPPORTED_MEDIA_TYPE, "不支持的 Content-Type: " + contentType);
        }

        if (lower.endsWith(".pdf") && !primary.equals("application/pdf")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "文件扩展名与内容类型不匹配");
        }
    }

    private static String primaryMimeType(String contentType) {
        String t = contentType.strip().toLowerCase(Locale.ROOT);
        int sc = t.indexOf(';');
        return sc < 0 ? t : t.substring(0, sc).strip();
    }

    private static boolean isImageExtension(String lowerFilename) {
        return lowerFilename.endsWith(".png")
                || lowerFilename.endsWith(".jpg")
                || lowerFilename.endsWith(".jpeg")
                || lowerFilename.endsWith(".webp")
                || lowerFilename.endsWith(".gif");
    }

    private static boolean isVideoExtension(String lowerFilename) {
        return lowerFilename.endsWith(".mp4")
                || lowerFilename.endsWith(".mov")
                || lowerFilename.endsWith(".m4v")
                || lowerFilename.endsWith(".webm")
                || lowerFilename.endsWith(".avi");
    }

    private static void assertHttpUrl(String raw) {
        URI uri;
        try {
            uri = URI.create(raw);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "URL 无效");
        }
        String scheme = uri.getScheme();
        if (scheme == null
                || (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "仅支持 http/https URL");
        }
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "URL 缺少主机名");
        }
    }

    private static String slugifyFilename(String title) {
        String s = title.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9\\u4e00-\\u9fa5]+", "-");
        s = s.replaceAll("^-+|-+$", "");
        if (s.isBlank()) {
            return "web-import";
        }
        return s.length() > 80 ? s.substring(0, 80) : s;
    }

    private static String truncateErr(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }

    private static String sha256(byte[] bytes) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(bytes);
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(String.format(Locale.ROOT, "%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "计算文件摘要失败");
        }
    }
}

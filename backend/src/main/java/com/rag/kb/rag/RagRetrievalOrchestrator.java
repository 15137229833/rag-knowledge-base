package com.rag.kb.rag;

import com.rag.kb.config.RagRetrievalProperties;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;

/**
 * FTS-only 检索：仅使用 PostgreSQL 全文检索，可选 rerank。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RagRetrievalOrchestrator {

    private static final List<String> LOW_VALUE_PLACEHOLDER_MARKERS = List.of(
            "已关闭视觉描述",
            "仅可凭文件名与占位说明检索",
            "视觉描述暂不可用",
            "视频视觉摘要暂不可用",
            "仅按文件名占位入库",
            "请配置 Qwen / DashScope 多模态模型",
            "请确认服务器已安装 ffmpeg");

    private static final List<String> WEB_NOISE_MARKERS = List.of(
            "[a-media]",
            "[iframe]",
            "[embed]",
            "[video]",
            "[video-source]",
            "[object-data]",
            "登录",
            "注册",
            "下载客户端",
            "打开App",
            "APP下载",
            "首页",
            "频道",
            "直播",
            "排行榜",
            "专题",
            "活动",
            "社区",
            "更多");

    private final PgVectorFtsRepository ftsRepository;
    private final OllamaRerankClient rerankClient;
    private final RagRetrievalProperties ragProps;

    public record RetrievalOutcome(int retrievedCandidates, List<Document> docsForLlm) {}

    public RetrievalOutcome retrieve(UUID kbId, String query, int userRequestedChunks) {
        String kb = kbId.toString();
        int req = Math.max(1, userRequestedChunks);
        int wantForLlm =
                ragProps.getRerank().isEnabled()
                        ? Math.min(req, Math.max(1, ragProps.getRerank().getTopNForLlm()))
                        : req;

        List<PgVectorFtsRepository.FtsRow> ftsRows =
                ftsRepository.searchByKb(kb, query, Math.max(req, ragProps.getHybrid().getRecallFtsK()));
        int candidateCount = ftsRows.size();
        if (ftsRows.isEmpty()) {
            return new RetrievalOutcome(0, List.of());
        }

        List<Document> docs = ftsRows.stream()
                .map(row -> new Document(row.id(), row.content(), row.metadata()))
                .toList();

        List<Document> preferredDocs = docs.stream().filter(doc -> !isLowValueDoc(doc)).toList();
        List<Document> baseDocs = preferredDocs.isEmpty() ? docs : preferredDocs;

        List<Document> forLlm = applyRerank(query, baseDocs, wantForLlm);
        return new RetrievalOutcome(candidateCount, forLlm);
    }

    private List<Document> applyRerank(String query, List<Document> docs, int wantForLlm) {
        if (!ragProps.getRerank().isEnabled()) {
            return docs.stream().limit(wantForLlm).toList();
        }
        List<String> texts = docs.stream().map(Document::getText).toList();
        List<Integer> order = rerankClient.rerankIndices(query, texts);
        if (order.isEmpty()) {
            log.info("Rerank skipped or failed; using FTS order, top {}", wantForLlm);
            return docs.stream().limit(wantForLlm).toList();
        }
        List<Document> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (int idx : order) {
            if (idx >= 0 && idx < docs.size()) {
                Document d = docs.get(idx);
                if (seen.add(d.getId())) {
                    out.add(d);
                }
            }
            if (out.size() >= wantForLlm) {
                break;
            }
        }
        if (out.size() < wantForLlm) {
            for (Document d : docs) {
                if (seen.add(d.getId())) {
                    out.add(d);
                }
                if (out.size() >= wantForLlm) {
                    break;
                }
            }
        }
        return out;
    }

    private static boolean isLowValueDoc(Document doc) {
        return isLowValuePlaceholder(doc) || isNavigationLikeWebNoise(doc);
    }

    private static boolean isLowValuePlaceholder(Document doc) {
        if (doc == null) {
            return false;
        }
        Object modality = doc.getMetadata().get("modality");
        boolean isMedia = "image".equals(modality) || "video".equals(modality);
        if (!isMedia) {
            return false;
        }
        String text = doc.getText();
        if (text == null || text.isBlank()) {
            return true;
        }
        return LOW_VALUE_PLACEHOLDER_MARKERS.stream().anyMatch(text::contains);
    }

    private static boolean isNavigationLikeWebNoise(Document doc) {
        if (doc == null) {
            return false;
        }
        Object modality = doc.getMetadata().get("modality");
        if (modality != null && !"text".equals(modality)) {
            return false;
        }
        String text = doc.getText();
        if (text == null || text.isBlank()) {
            return true;
        }
        String normalized = text.toLowerCase(Locale.ROOT);
        int markerHits = 0;
        for (String marker : WEB_NOISE_MARKERS) {
            if (normalized.contains(marker.toLowerCase(Locale.ROOT))) {
                markerHits++;
            }
        }
        int urlHits = countOccurrences(normalized, "http://")
                + countOccurrences(normalized, "https://")
                + countOccurrences(normalized, "www.");
        int mediaTagHits = countOccurrences(normalized, "[a-media]")
                + countOccurrences(normalized, "[iframe]")
                + countOccurrences(normalized, "[embed]")
                + countOccurrences(normalized, "[video]")
                + countOccurrences(normalized, "[video-source]");
        int shortTokenCount = countShortTokens(text);

        if (mediaTagHits >= 2) {
            return true;
        }
        if (urlHits >= 3) {
            return true;
        }
        if (markerHits >= 6 && shortTokenCount >= 8) {
            return true;
        }
        return text.length() < 220 && markerHits >= 5;
    }

    private static int countOccurrences(String text, String needle) {
        int count = 0;
        int from = 0;
        while (from >= 0) {
            int idx = text.indexOf(needle, from);
            if (idx < 0) {
                break;
            }
            count++;
            from = idx + needle.length();
        }
        return count;
    }

    private static int countShortTokens(String text) {
        String[] parts = text.split("\\s+");
        int count = 0;
        for (String p : parts) {
            String t = p.trim();
            if (!t.isEmpty() && t.length() <= 4) {
                count++;
            }
        }
        return count;
    }

    public static int hashString(String str) {
        if (str == null) {
            return 0;
        }
        return str.hashCode();
    }
}

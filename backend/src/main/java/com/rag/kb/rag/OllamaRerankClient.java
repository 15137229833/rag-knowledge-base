package com.rag.kb.rag;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rag.kb.config.RagRetrievalProperties;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * 调用 Ollama <a href="https://github.com/ollama/ollama">/api/rerank</a> 对候选文档精排。
 * 若当前 Ollama 版本无该接口，将返回 empty，由上层回退到融合排序。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OllamaRerankClient {

    private final RagRetrievalProperties ragProps;
    private final ObjectMapper objectMapper;

    @Value("${spring.ai.ollama.base-url:http://localhost:11434}")
    private String ollamaBaseUrl;

    /**
     * @return 按相关度降序的「输入 documents 列表中的下标」；失败时返回 empty
     */
    public List<Integer> rerankIndices(String query, List<String> documents) {
        if (!ragProps.getRerank().isEnabled()
                || documents == null
                || documents.isEmpty()
                || query == null
                || query.isBlank()) {
            return List.of();
        }
        String model = ragProps.getRerank().getModel();
        long timeout = Math.max(5_000L, ragProps.getRerank().getTimeoutMs());
        SimpleClientHttpRequestFactory rf = new SimpleClientHttpRequestFactory();
        rf.setConnectTimeout(10_000);
        rf.setReadTimeout((int) Math.min(timeout, Integer.MAX_VALUE));
        RestClient client =
                RestClient.builder()
                        .requestFactory(rf)
                        .baseUrl(trimSlash(ollamaBaseUrl))
                        .build();

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", model);
        payload.put("query", query);
        payload.put("documents", documents);

        try {
            String json = objectMapper.writeValueAsString(payload);
            String raw =
                    client.post()
                            .uri("/api/rerank")
                            .contentType(MediaType.APPLICATION_JSON)
                            .body(json)
                            .retrieve()
                            .body(String.class);
            if (raw == null || raw.isBlank()) {
                return List.of();
            }
            return parseRerankResponse(raw, documents.size());
        } catch (RestClientException e) {
            log.warn("Ollama rerank unavailable or failed: {}", e.getMessage());
            return List.of();
        } catch (Exception e) {
            log.warn("Ollama rerank parse error: {}", e.getMessage());
            return List.of();
        }
    }

    private List<Integer> parseRerankResponse(String raw, int docCount) throws Exception {
        JsonNode root = objectMapper.readTree(raw);
        JsonNode results = root.get("results");
        if (results == null || !results.isArray()) {
            results = root.get("rankings");
        }
        if (results == null || !results.isArray()) {
            return List.of();
        }
        record IdxScore(int index, double score) {}
        List<IdxScore> list = new ArrayList<>();
        for (JsonNode n : results) {
            if (n == null || !n.isObject()) {
                continue;
            }
            int idx = n.path("index").asInt(-1);
            double score = n.path("relevance_score").asDouble(Double.NaN);
            if (Double.isNaN(score)) {
                score = n.path("score").asDouble(0d);
            }
            if (idx >= 0 && idx < docCount) {
                list.add(new IdxScore(idx, score));
            }
        }
        if (list.isEmpty()) {
            return List.of();
        }
        list.sort(Comparator.comparingDouble(IdxScore::score).reversed());
        return list.stream().map(IdxScore::index).toList();
    }

    private static String trimSlash(String u) {
        if (u == null) {
            return "http://localhost:11434";
        }
        return u.endsWith("/") ? u.substring(0, u.length() - 1) : u;
    }
}

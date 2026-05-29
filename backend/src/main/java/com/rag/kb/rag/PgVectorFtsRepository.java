package com.rag.kb.rag;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rag.kb.config.RagRetrievalProperties;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 在 Spring AI pgvector 物理表上对 content 做 PostgreSQL 全文检索（simple 配置）。
 */
@Repository
@RequiredArgsConstructor
@Slf4j
public class PgVectorFtsRepository {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final RagRetrievalProperties ragProps;

    public record FtsRow(String id, String content, Map<String, Object> metadata, double rank) {}

    /**
     * 按知识库 id 过滤，优先 FTS；若未命中则回退到 ILIKE；仍未命中则返回该知识库最近文本块。
     */
    public List<FtsRow> searchByKb(String kbId, String queryText, int limit) {
        if (queryText == null || queryText.isBlank() || limit <= 0) {
            return List.of();
        }
        String q = sanitizeFtsQuery(queryText);
        if (q.isBlank()) {
            return fallbackRecentByKb(kbId, limit, ragProps.getVectorTable());
        }
        String table = ragProps.getVectorTable();
        String sql =
                """
                SELECT id, content, metadata,
                       ts_rank(to_tsvector('simple', content), plainto_tsquery('simple', ?)) AS rank
                FROM %s
                WHERE metadata::jsonb->>'kbId' = ?
                  AND to_tsvector('simple', content) @@ plainto_tsquery('simple', ?)
                ORDER BY rank DESC
                LIMIT ?
                """
                        .formatted(table);
        try {
            List<FtsRow> rows = jdbcTemplate.query(
                    sql,
                    (rs, rowNum) -> mapRow(rs.getString("id"), rs.getString("content"), rs.getString("metadata"), rs.getDouble("rank")),
                    q,
                    kbId,
                    q,
                    limit);
            if (!rows.isEmpty()) {
                return rows;
            }
        } catch (Exception e) {
            log.warn("FTS search failed (table={}, kbId={}): {}", table, kbId, e.getMessage());
        }

        List<FtsRow> likeRows = fallbackSearchByLike(kbId, queryText, limit, table);
        if (!likeRows.isEmpty()) {
            return likeRows;
        }
        return fallbackRecentByKb(kbId, limit, table);
    }

    private List<FtsRow> fallbackSearchByLike(String kbId, String queryText, int limit, String table) {
        String normalized = normalizeLikeQuery(queryText);
        if (normalized.isBlank()) {
            return List.of();
        }
        String sql =
                """
                SELECT id, content, metadata,
                       CASE WHEN content ILIKE ? THEN 1.0 ELSE 0.2 END AS rank
                FROM %s
                WHERE metadata::jsonb->>'kbId' = ?
                  AND content ILIKE ?
                ORDER BY rank DESC, id DESC
                LIMIT ?
                """
                        .formatted(table);
        String exactLike = "%" + normalized + "%";
        try {
            return jdbcTemplate.query(
                    sql,
                    (rs, rowNum) -> mapRow(rs.getString("id"), rs.getString("content"), rs.getString("metadata"), rs.getDouble("rank")),
                    exactLike,
                    kbId,
                    exactLike,
                    limit);
        } catch (Exception e) {
            log.warn("LIKE fallback search failed (table={}, kbId={}): {}", table, kbId, e.getMessage());
            return List.of();
        }
    }

    private List<FtsRow> fallbackRecentByKb(String kbId, int limit, String table) {
        String sql =
                """
                SELECT id, content, metadata, 0.05 AS rank
                FROM %s
                WHERE metadata::jsonb->>'kbId' = ?
                ORDER BY id DESC
                LIMIT ?
                """
                        .formatted(table);
        try {
            return jdbcTemplate.query(
                    sql,
                    (rs, rowNum) -> mapRow(rs.getString("id"), rs.getString("content"), rs.getString("metadata"), rs.getDouble("rank")),
                    kbId,
                    limit);
        } catch (Exception e) {
            log.warn("Recent fallback search failed (table={}, kbId={}): {}", table, kbId, e.getMessage());
            return List.of();
        }
    }

    private FtsRow mapRow(String id, String content, String metaRaw, double rank) {
        return new FtsRow(id, content, parseMetadata(metaRaw), rank);
    }

    private Map<String, Object> parseMetadata(String metaRaw) {
        if (metaRaw == null || metaRaw.isBlank()) {
            return new HashMap<>();
        }
        try {
            return objectMapper.readValue(metaRaw, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            return new HashMap<>();
        }
    }

    private static String sanitizeFtsQuery(String raw) {
        return raw.replace('\u0000', ' ')
                .replace("'", " ")
                .replace("\\", " ")
                .trim();
    }

    private static String normalizeLikeQuery(String raw) {
        return raw.replace('\u0000', ' ')
                .replace("%", "")
                .replace("_", "")
                .trim();
    }
}

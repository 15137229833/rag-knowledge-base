package com.rag.kb.rag;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rag.kb.config.RagRetrievalProperties;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * FTS-only 模式下的文本块存储：仅写 content/metadata，不依赖 embedding。
 */
@Repository
@RequiredArgsConstructor
@Slf4j
public class TextChunkStoreRepository {

    private final JdbcTemplate jdbcTemplate;
    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;
    private final ObjectMapper objectMapper;
    private final RagRetrievalProperties ragProps;

    public void saveAll(List<Document> docs) {
        if (docs == null || docs.isEmpty()) {
            return;
        }
        String table = validatedTableName();
        String sql = "INSERT INTO " + table + " (id, content, metadata) VALUES (?::uuid, ?, ?::json)";
        jdbcTemplate.batchUpdate(sql, docs, docs.size(), (ps, doc) -> {
            ps.setString(1, doc.getId());
            ps.setString(2, doc.getText());
            ps.setString(3, writeMetadata(doc));
        });
    }

    public void deleteByIds(List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        String table = validatedTableName();
        String sql = "DELETE FROM " + table + " WHERE id IN (:ids)";
        namedParameterJdbcTemplate.update(sql, new MapSqlParameterSource("ids", ids));
    }

    public int deleteOrphanChunks(List<UUID> existingDocumentIds) {
        String table = validatedTableName();
        if (existingDocumentIds == null || existingDocumentIds.isEmpty()) {
            return jdbcTemplate.update("DELETE FROM " + table + " WHERE metadata ->> 'documentId' IS NOT NULL");
        }
        String sql = "DELETE FROM " + table
                + " WHERE metadata ->> 'documentId' IS NOT NULL"
                + " AND (metadata ->> 'documentId')::uuid NOT IN (:docIds)";
        return namedParameterJdbcTemplate.update(sql, new MapSqlParameterSource("docIds", existingDocumentIds));
    }

    public void ensureTable() {
        String table = validatedTableName();
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS %s (
                    id UUID PRIMARY KEY,
                    content TEXT,
                    metadata JSON,
                    embedding TEXT
                )
                """.formatted(table));
        jdbcTemplate.execute(
                "CREATE INDEX IF NOT EXISTS idx_" + table + "_content_fts ON " + table
                        + " USING GIN (to_tsvector('simple', content))");
        log.info("FTS chunk table ensured on {}", table);
    }

    private String writeMetadata(Document doc) {
        try {
            return objectMapper.writeValueAsString(doc.getMetadata());
        } catch (Exception e) {
            throw new IllegalStateException("序列化文档元数据失败", e);
        }
    }

    private String validatedTableName() {
        String table = ragProps.getVectorTable();
        if (table == null || !table.matches("[a-zA-Z0-9_]+")) {
            throw new IllegalStateException("非法文本块表名: " + table);
        }
        return table;
    }
}

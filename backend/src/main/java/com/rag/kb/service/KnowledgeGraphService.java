package com.rag.kb.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rag.kb.domain.ChatRecord;
import com.rag.kb.domain.DocumentStatus;
import com.rag.kb.domain.KbDocument;
import com.rag.kb.dto.ChatDtos.CitationDto;
import com.rag.kb.repository.ChatRecordRepository;
import com.rag.kb.repository.KbDocumentRepository;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 知识图谱（MVP）：节点为知识库文档，边为同一问答轮次引用中的文档共现关系。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeGraphService {

    private static final int MAX_CHATS_FOR_GRAPH = 800;
    private static final int MAX_EDGES = 400;
    private static final Pattern TOKEN_PATTERN = Pattern.compile("[\\p{L}\\p{N}]{2,}");

    private final ChatRecordRepository chatRecordRepository;
    private final KbDocumentRepository kbDocumentRepository;
    private final ObjectMapper objectMapper;
    private final KnowledgeBaseService knowledgeBaseService;

    public record Entity(
            UUID id, String name, String type, Map<String, Object> properties) {}

    public record Relation(
            UUID id, UUID fromEntityId, UUID toEntityId, String type, Map<String, Object> properties) {}

    public record GraphPath(List<Entity> entities, List<Relation> relations) {}

    public record GraphVisualization(List<Entity> nodes, List<Relation> edges) {}

    /** 占位：后续可接 NER/Neo4j 离线构建 */
    public void buildGraph(UUID kbId, UUID userId) {
        knowledgeBaseService.requireReadable(kbId, userId);
        log.info("知识库 {} 图谱构建任务已记录（当前为引用共现图谱，无需离线构建）", kbId);
    }

    @Transactional(readOnly = true)
    public GraphPath searchGraph(UUID kbId, String query, UUID userId) {
        knowledgeBaseService.requireReadable(kbId, userId);
        GraphVisualization full = buildCoCitationGraph(kbId);
        if (query == null || query.isBlank()) {
            return new GraphPath(full.nodes(), full.edges());
        }
        String q = query.trim().toLowerCase(Locale.ROOT);
        Set<UUID> matched =
                full.nodes().stream()
                        .filter(n -> n.name() != null && n.name().toLowerCase(Locale.ROOT).contains(q))
                        .map(Entity::id)
                        .collect(Collectors.toSet());
        if (matched.isEmpty()) {
            return new GraphPath(List.of(), List.of());
        }
        Set<UUID> keep = new LinkedHashSet<>(matched);
        for (Relation e : full.edges()) {
            if (matched.contains(e.fromEntityId()) || matched.contains(e.toEntityId())) {
                keep.add(e.fromEntityId());
                keep.add(e.toEntityId());
            }
        }
        List<Entity> entities =
                full.nodes().stream().filter(n -> keep.contains(n.id())).toList();
        List<Relation> rels =
                full.edges().stream()
                        .filter(
                                e ->
                                        keep.contains(e.fromEntityId())
                                                && keep.contains(e.toEntityId()))
                        .toList();
        return new GraphPath(entities, rels);
    }

    @Transactional(readOnly = true)
    public GraphVisualization getVisualization(UUID kbId, UUID userId) {
        knowledgeBaseService.requireReadable(kbId, userId);
        return buildCoCitationGraph(kbId);
    }

    public List<Entity> extractEntities(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        Set<String> seen = new LinkedHashSet<>();
        Matcher m = TOKEN_PATTERN.matcher(text);
        while (m.find() && seen.size() < 80) {
            seen.add(m.group());
        }
        return seen.stream()
                .map(
                        w ->
                                new Entity(
                                        UUID.nameUUIDFromBytes(w.getBytes(StandardCharsets.UTF_8)),
                                        w,
                                        "token",
                                        Map.of()))
                .toList();
    }

    public List<Relation> extractRelations(String text, List<Entity> entities) {
        return List.of();
    }

    private GraphVisualization buildCoCitationGraph(UUID kbId) {
        List<KbDocument> docs =
                kbDocumentRepository.findByKnowledgeBaseIdAndStatus(kbId, DocumentStatus.READY);
        Map<UUID, KbDocument> docById = new HashMap<>();
        for (KbDocument d : docs) {
            docById.put(d.getId(), d);
        }

        List<ChatRecord> chats =
                chatRecordRepository.findByKnowledgeBaseIdOrderByCreatedAtDesc(
                        kbId, PageRequest.of(0, MAX_CHATS_FOR_GRAPH));

        Map<String, Integer> edgeWeight = new HashMap<>();
        for (ChatRecord cr : chats) {
            List<UUID> cited = parseCitationDocIds(cr.getCitationsJson());
            if (cited.size() < 2) {
                continue;
            }
            List<UUID> inKb =
                    cited.stream().distinct().filter(docById::containsKey).sorted().toList();
            for (int i = 0; i < inKb.size(); i++) {
                for (int j = i + 1; j < inKb.size(); j++) {
                    UUID a = inKb.get(i);
                    UUID b = inKb.get(j);
                    String key = a + "|" + b;
                    edgeWeight.merge(key, 1, Integer::sum);
                }
            }
        }

        List<Map.Entry<String, Integer>> topEdges =
                edgeWeight.entrySet().stream()
                        .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                        .limit(MAX_EDGES)
                        .toList();

        Set<UUID> nodeIds = new LinkedHashSet<>();
        for (Map.Entry<String, Integer> e : topEdges) {
            String[] parts = e.getKey().split("\\|");
            nodeIds.add(UUID.fromString(parts[0]));
            nodeIds.add(UUID.fromString(parts[1]));
        }

        List<Entity> nodes = new ArrayList<>();
        for (UUID id : nodeIds) {
            KbDocument d = docById.get(id);
            if (d == null) {
                continue;
            }
            Map<String, Object> props = new LinkedHashMap<>();
            props.put("filename", d.getFilename());
            props.put("sizeBytes", d.getSizeBytes());
            nodes.add(new Entity(d.getId(), d.getFilename(), "document", props));
        }

        List<Relation> edges = new ArrayList<>();
        int seq = 0;
        for (Map.Entry<String, Integer> e : topEdges) {
            String[] parts = e.getKey().split("\\|");
            UUID from = UUID.fromString(parts[0]);
            UUID to = UUID.fromString(parts[1]);
            if (!docById.containsKey(from) || !docById.containsKey(to)) {
                continue;
            }
            UUID relId = UUID.nameUUIDFromBytes((e.getKey() + seq).getBytes(StandardCharsets.UTF_8));
            edges.add(
                    new Relation(
                            relId,
                            from,
                            to,
                            "co_cited",
                            Map.of("weight", e.getValue())));
            seq++;
        }

        return new GraphVisualization(nodes, edges);
    }

    private List<UUID> parseCitationDocIds(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            List<CitationDto> list =
                    objectMapper.readValue(json, new TypeReference<List<CitationDto>>() {});
            return list.stream()
                    .map(CitationDto::documentId)
                    .filter(Objects::nonNull)
                    .toList();
        } catch (Exception ex) {
            log.debug("解析 citations_json 失败: {}", ex.getMessage());
            return List.of();
        }
    }
}

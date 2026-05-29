package com.rag.kb.api;

import com.rag.kb.security.SecurityUtils;
import com.rag.kb.service.KnowledgeBaseService;
import com.rag.kb.service.KnowledgeGraphService;
import com.rag.kb.service.KnowledgeGraphService.Entity;
import com.rag.kb.service.KnowledgeGraphService.GraphPath;
import com.rag.kb.service.KnowledgeGraphService.GraphVisualization;
import com.rag.kb.service.KnowledgeGraphService.Relation;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/kb/{kbId}/graph")
@RequiredArgsConstructor
@Tag(name = "Knowledge graph", description = "引用共现知识图谱")
public class KnowledgeGraphController {

    private final KnowledgeGraphService knowledgeGraphService;
    private final KnowledgeBaseService knowledgeBaseService;

    @GetMapping("/visualization")
    @Operation(summary = "图谱可视化数据（文档节点 + 共现边）")
    public GraphVisualization visualization(@PathVariable UUID kbId) {
        return knowledgeGraphService.getVisualization(kbId, SecurityUtils.getCurrentUserId());
    }

    @GetMapping("/search")
    @Operation(summary = "按文档名关键字筛选子图")
    public GraphPath search(
            @PathVariable UUID kbId, @RequestParam(required = false) String q) {
        return knowledgeGraphService.searchGraph(kbId, q, SecurityUtils.getCurrentUserId());
    }

    @PostMapping("/build")
    @Operation(summary = "触发图谱构建（当前为占位，数据实时从引用统计）")
    public void build(@PathVariable UUID kbId) {
        knowledgeGraphService.buildGraph(kbId, SecurityUtils.getCurrentUserId());
    }

    public record ExtractEntitiesRequest(String text) {}

    @PostMapping("/entities/extract")
    @Operation(summary = "从文本抽取简单词元实体（演示）")
    public List<Entity> extractEntities(
            @PathVariable UUID kbId, @RequestBody ExtractEntitiesRequest body) {
        knowledgeBaseService.requireReadable(kbId, SecurityUtils.getCurrentUserId());
        return knowledgeGraphService.extractEntities(body == null ? null : body.text());
    }

    public record ExtractRelationsRequest(String text, List<Entity> entities) {}

    @PostMapping("/relations/extract")
    @Operation(summary = "关系抽取占位，返回空列表")
    public List<Relation> extractRelations(
            @PathVariable UUID kbId, @RequestBody(required = false) ExtractRelationsRequest body) {
        knowledgeBaseService.requireReadable(kbId, SecurityUtils.getCurrentUserId());
        List<Entity> ents = body == null ? List.of() : body.entities();
        String t = body == null ? "" : body.text();
        return knowledgeGraphService.extractRelations(t, ents);
    }
}

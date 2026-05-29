package com.rag.kb.api;

import com.rag.kb.dto.DocumentDtos.DocumentResponse;
import com.rag.kb.security.SecurityUtils;
import com.rag.kb.service.AnalyticsService;
import com.rag.kb.service.KnowledgeBaseService;
import com.rag.kb.service.RecommendationService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * 推荐与分析 API
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class RecommendationController {

    private final RecommendationService recommendationService;
    private final AnalyticsService analyticsService;
    private final KnowledgeBaseService knowledgeBaseService;

    /**
     * 推荐相关文档
     */
    @GetMapping("/kb/{kbId}/recommendations/documents")
    public ResponseEntity<List<DocumentResponse>> recommendDocuments(
            @PathVariable UUID kbId,
            @RequestParam(defaultValue = "5") int limit) {
        UUID userId = SecurityUtils.getCurrentUserId();
        knowledgeBaseService.requireReadable(kbId, userId);
        List<DocumentResponse> docs = recommendationService.recommendDocuments(userId, kbId, limit);
        return ResponseEntity.ok(docs);
    }

    /**
     * 推荐相关问题
     */
    @GetMapping("/kb/{kbId}/recommendations/questions")
    public ResponseEntity<List<String>> recommendQuestions(
            @PathVariable UUID kbId,
            @RequestParam String question,
            @RequestParam(defaultValue = "5") int limit) {
        UUID userId = SecurityUtils.getCurrentUserId();
        knowledgeBaseService.requireReadable(kbId, userId);
        List<String> questions = recommendationService.recommendQuestions(userId, kbId, question, limit);
        return ResponseEntity.ok(questions);
    }

    /**
     * 获取使用统计
     */
    @GetMapping("/analytics/usage")
    public ResponseEntity<AnalyticsService.UsageStats> getUsageStats(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end) {
        UUID userId = SecurityUtils.getCurrentUserId();
        AnalyticsService.UsageStats stats = analyticsService.getUsageStats(userId, start, end);
        return ResponseEntity.ok(stats);
    }

    /**
     * 获取热门文档
     */
    @GetMapping("/kb/{kbId}/analytics/hot-documents")
    public ResponseEntity<List<AnalyticsService.HotDocument>> getHotDocuments(
            @PathVariable UUID kbId,
            @RequestParam(defaultValue = "10") int limit) {
        UUID userId = SecurityUtils.getCurrentUserId();
        knowledgeBaseService.requireReadable(kbId, userId);
        List<AnalyticsService.HotDocument> docs = analyticsService.getHotDocuments(kbId, limit);
        return ResponseEntity.ok(docs);
    }

    /**
     * 获取用户活跃度
     */
    @GetMapping("/kb/{kbId}/analytics/user-activity")
    public ResponseEntity<List<AnalyticsService.UserActivity>> getUserActivity(
            @PathVariable UUID kbId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end) {
        UUID userId = SecurityUtils.getCurrentUserId();
        knowledgeBaseService.requireReadable(kbId, userId);
        List<AnalyticsService.UserActivity> activity = analyticsService.getUserActivity(kbId, start, end);
        return ResponseEntity.ok(activity);
    }

    /**
     * 获取知识库概览
     */
    @GetMapping("/kb/{kbId}/analytics/overview")
    public ResponseEntity<AnalyticsService.KbOverview> getKbOverview(@PathVariable UUID kbId) {
        UUID userId = SecurityUtils.getCurrentUserId();
        knowledgeBaseService.requireReadable(kbId, userId);
        AnalyticsService.KbOverview overview = analyticsService.getKbOverview(kbId);
        return ResponseEntity.ok(overview);
    }
}

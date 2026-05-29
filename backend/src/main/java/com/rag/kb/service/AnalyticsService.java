package com.rag.kb.service;

import com.rag.kb.domain.ChatRecord;
import com.rag.kb.domain.DocumentStatus;
import com.rag.kb.domain.KbDocument;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rag.kb.dto.ChatDtos.CitationDto;
import com.rag.kb.repository.ChatRecordRepository;
import com.rag.kb.repository.KbDocumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 数据分析服务
 * 
 * 提供系统使用数据分析和可视化
 * 
 * 价值：
 * - 提供数据洞察
 * - 支持决策优化
 * - 增强系统价值
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AnalyticsService {

    private final ChatRecordRepository chatRecordRepository;
    private final KbDocumentRepository kbDocumentRepository;
    private final ObjectMapper objectMapper;

    /**
     * 使用统计数据
     */
    @Cacheable(value = "analytics", 
            key = "'usage:' + #userId + ':' + #start + ':' + #end")
    @Transactional(readOnly = true)
    public UsageStats getUsageStats(UUID userId, LocalDate start, LocalDate end) {
        log.debug("获取用户 {} 从 {} 到 {} 的使用统计", userId, start, end);
        
        Instant startInstant = start.atStartOfDay(ZoneId.systemDefault()).toInstant();
        Instant endInstant = end.atTime(23, 59, 59).atZone(ZoneId.systemDefault()).toInstant();
        
        // 查询总数
        long totalQuestions = chatRecordRepository
                .countByUserIdAndCreatedAtBetween(userId, startInstant, endInstant);
        
        long totalDocuments = kbDocumentRepository
                .countByCreatedAtBetween(startInstant, endInstant);
        
        // 平均响应时间（简化实现）
        double avgResponseTime = calculateAvgResponseTime(userId, startInstant, endInstant);
        
        // 满意度率（基于反馈）
        double satisfactionRate = calculateSatisfactionRate(userId, startInstant, endInstant);
        
        // 每日使用情况
        List<DailyUsage> dailyUsage = getDailyUsage(userId, start, end);
        
        return new UsageStats(
                totalQuestions,
                totalDocuments,
                avgResponseTime,
                satisfactionRate,
                dailyUsage
        );
    }

    /**
     * 热门文档
     */
    @Cacheable(value = "analytics", 
            key = "'hotDocs:' + #kbId + ':' + #limit")
    @Transactional(readOnly = true)
    public List<HotDocument> getHotDocuments(UUID kbId, int limit) {
        log.debug("获取知识库 {} 的热门文档", kbId);
        
        // 统计被引用最多的文档
        List<ChatRecord> chats = chatRecordRepository
                .findByKnowledgeBaseIdOrderByCreatedAtDesc(kbId, PageRequest.of(0, 1000));
        
        Map<UUID, Integer> docRefCount = new HashMap<>();

        for (ChatRecord chat : chats) {
            for (UUID docId : parseCitationDocumentIds(chat.getCitationsJson())) {
                docRefCount.merge(docId, 1, Integer::sum);
            }
        }
        
        return docRefCount.entrySet().stream()
                .sorted(Map.Entry.<UUID, Integer>comparingByValue().reversed())
                .limit(limit)
                .map(entry -> {
                    KbDocument doc = kbDocumentRepository.findById(entry.getKey()).orElse(null);
                    if (doc != null) {
                        return new HotDocument(
                                doc.getId(),
                                doc.getFilename(),
                                doc.getFilename(),
                                entry.getValue()
                        );
                    }
                    return null;
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    /**
     * 用户活跃度
     */
    @Cacheable(value = "analytics", 
            key = "'userActivity:' + #kbId + ':' + #start + ':' + #end")
    @Transactional(readOnly = true)
    public List<UserActivity> getUserActivity(UUID kbId, LocalDate start, LocalDate end) {
        log.debug("获取知识库 {} 从 {} 到 {} 的用户活跃度", kbId, start, end);
        
        Instant startInstant = start.atStartOfDay(ZoneId.systemDefault()).toInstant();
        Instant endInstant = end.atTime(23, 59, 59).atZone(ZoneId.systemDefault()).toInstant();
        
        List<ChatRecord> chats = chatRecordRepository
                .findByKnowledgeBaseIdAndCreatedAtBetween(kbId, startInstant, endInstant);
        
        // 按用户统计
        Map<UUID, UserActivityStats> userStats = new HashMap<>();
        ZoneId z = ZoneId.systemDefault();
        for (ChatRecord chat : chats) {
            UUID uid = chat.getUser().getId();
            userStats.computeIfAbsent(uid, k -> new UserActivityStats()).add(chat, z);
        }

        return userStats.entrySet().stream()
                .sorted(Map.Entry.<UUID, UserActivityStats>comparingByValue().reversed())
                .limit(20)
                .map(
                        entry ->
                                new UserActivity(
                                        entry.getKey(),
                                        entry.getValue().getQuestionCount(),
                                        entry.getValue().getActiveDayCount()))
                .collect(Collectors.toList());
    }

    /**
     * 知识库概览
     */
    @Cacheable(value = "analytics", 
            key = "'kbOverview:' + #kbId")
    @Transactional(readOnly = true)
    public KbOverview getKbOverview(UUID kbId) {
        log.debug("获取知识库 {} 的概览", kbId);
        
        long totalDocuments = kbDocumentRepository
                .countByKnowledgeBaseId(kbId);
        
        long ingestedDocuments = kbDocumentRepository
                .countByKnowledgeBaseIdAndStatus(kbId, DocumentStatus.READY);
        
        long totalQuestions = chatRecordRepository
                .countByKnowledgeBaseId(kbId);
        
        // 最近7天的活跃度
        LocalDate now = LocalDate.now();
        LocalDate weekAgo = now.minusDays(7);
        Instant weekAgoInstant = weekAgo.atStartOfDay(ZoneId.systemDefault()).toInstant();
        
        long recentQuestions = chatRecordRepository
                .countByKnowledgeBaseIdAndCreatedAtAfter(kbId, weekAgoInstant);
        
        return new KbOverview(
                totalDocuments,
                ingestedDocuments,
                totalQuestions,
                recentQuestions
        );
    }

    // ========== 辅助方法 ==========

    private double calculateAvgResponseTime(UUID userId, Instant start, Instant end) {
        return chatRecordRepository
                .averageLatencyMsByUserAndCreatedAtBetween(userId, start, end)
                .map(ms -> ms / 1000.0)
                .orElse(0.0);
    }

    private double calculateSatisfactionRate(UUID userId, Instant start, Instant end) {
        long withFeedback =
                chatRecordRepository.countFeedbackByUserAndCreatedAtBetween(userId, start, end);
        if (withFeedback == 0) {
            return 0.0;
        }
        long positive =
                chatRecordRepository.countPositiveFeedbackByUserAndCreatedAtBetween(
                        userId, start, end);
        return (double) positive / withFeedback;
    }

    private List<DailyUsage> getDailyUsage(UUID userId, LocalDate start, LocalDate end) {
        List<DailyUsage> dailyUsage = new ArrayList<>();
        
        LocalDate current = start;
        while (!current.isAfter(end)) {
            Instant dayStart = current.atStartOfDay(ZoneId.systemDefault()).toInstant();
            Instant dayEnd = current.atTime(23, 59, 59).atZone(ZoneId.systemDefault()).toInstant();
            
            long count = chatRecordRepository
                    .countByUserIdAndCreatedAtBetween(userId, dayStart, dayEnd);
            
            dailyUsage.add(new DailyUsage(current, count));
            current = current.plusDays(1);
        }
        
        return dailyUsage;
    }

    // ========== 数据类 ==========

    public record UsageStats(
            long totalQuestions,
            long totalDocuments,
            double avgResponseTime,
            double satisfactionRate,
            List<DailyUsage> dailyUsage
    ) {}

    public record DailyUsage(
            LocalDate date,
            long count
    ) {}

    public record HotDocument(
            UUID documentId,
            String title,
            String fileName,
            int referenceCount
    ) {}

    public record UserActivity(
            UUID userId,
            long questionCount,
            long activeDays
    ) {}

    public record KbOverview(
            long totalDocuments,
            long ingestedDocuments,
            long totalQuestions,
            long recentQuestions
    ) {}

    private List<UUID> parseCitationDocumentIds(String json) {
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
        } catch (Exception e) {
            log.debug("解析 citations_json 失败: {}", e.getMessage());
            return List.of();
        }
    }

    private static class UserActivityStats implements Comparable<UserActivityStats> {
        private long questionCount = 0;
        private final Set<LocalDate> activeDays = new HashSet<>();

        void add(ChatRecord chat, ZoneId zone) {
            questionCount++;
            if (chat.getCreatedAt() != null) {
                activeDays.add(LocalDate.ofInstant(chat.getCreatedAt(), zone));
            }
        }

        long getQuestionCount() {
            return questionCount;
        }

        long getActiveDayCount() {
            return activeDays.size();
        }

        @Override
        public int compareTo(UserActivityStats other) {
            return Long.compare(other.questionCount, this.questionCount);
        }
    }
}

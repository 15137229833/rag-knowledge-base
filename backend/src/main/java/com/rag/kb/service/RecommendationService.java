package com.rag.kb.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rag.kb.config.CacheConfig;
import com.rag.kb.domain.ChatRecord;
import com.rag.kb.domain.DocumentStatus;
import com.rag.kb.domain.KbDocument;
import com.rag.kb.dto.ChatDtos.CitationDto;
import com.rag.kb.dto.DocumentDtos.DocumentResponse;
import com.rag.kb.repository.ChatRecordRepository;
import com.rag.kb.repository.KbDocumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 智能推荐服务
 * 
 * 基于用户行为和问答历史，智能推荐相关文档和问题
 * 
 * 价值：
 * - 提升用户粘性
 * - 增加文档利用率
 * - 改善用户体验
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RecommendationService {

    private final ChatRecordRepository chatRecordRepository;
    private final KbDocumentRepository kbDocumentRepository;
    private final ObjectMapper objectMapper;

    /**
     * 推荐相关文档
     * 
     * @param userId 用户ID
     * @param kbId 知识库ID
     * @param limit 推荐数量
     * @return 推荐文档列表
     */
    @Cacheable(value = CacheConfig.DOCUMENT_CACHE, 
            key = "'recommend:' + #userId + ':' + #kbId",
            unless = "#result.isEmpty()")
    @Transactional(readOnly = true)
    public List<DocumentResponse> recommendDocuments(UUID userId, UUID kbId, int limit) {
        log.debug("为用户 {} 在知识库 {} 中推荐文档", userId, kbId);
        
        // 1. 获取用户最近问答记录
        List<ChatRecord> recentChats = chatRecordRepository
                .findByUserIdAndKnowledgeBaseIdOrderByCreatedAtDesc(userId, kbId, PageRequest.of(0, 10));
        
        if (recentChats.isEmpty()) {
            // 如果没有历史记录，返回热门文档
            return getPopularDocuments(kbId, limit);
        }
        
        // 2. 提取关键词
        Set<String> keywords = extractKeywords(recentChats);
        
        if (keywords.isEmpty()) {
            return getPopularDocuments(kbId, limit);
        }
        
        // 3. 基于关键词检索相关文档
        List<KbDocument> candidateDocs = kbDocumentRepository
                .findByKnowledgeBaseIdAndStatus(kbId, DocumentStatus.READY);
        
        // 4. 计算文档相关性分数
        Map<UUID, Double> docScores = new HashMap<>();
        for (KbDocument doc : candidateDocs) {
            double score = calculateRelevanceScore(doc, keywords, recentChats);
            if (score > 0) {
                docScores.put(doc.getId(), score);
            }
        }
        
        // 5. 排序并返回 Top-N
        return docScores.entrySet().stream()
                .sorted(Map.Entry.<UUID, Double>comparingByValue().reversed())
                .limit(limit)
                .map(entry -> {
                    KbDocument doc = kbDocumentRepository.findById(entry.getKey()).orElse(null);
                    if (doc != null) {
                        return toDocumentResponse(doc, entry.getValue());
                    }
                    return null;
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    /**
     * 推荐相关问题
     * 
     * @param userId 用户ID
     * @param kbId 知识库ID
     * @param currentQuestion 当前问题
     * @param limit 推荐数量
     * @return 推荐问题列表
     */
    @Cacheable(value = CacheConfig.QA_CACHE, 
            key = "'suggest:' + #kbId + ':' + T(com.rag.kb.rag.RagRetrievalOrchestrator).hashString(#currentQuestion)",
            unless = "#result.isEmpty()")
    @Transactional(readOnly = true)
    public List<String> recommendQuestions(UUID userId, UUID kbId, String currentQuestion, int limit) {
        log.debug("为用户 {} 在知识库 {} 中推荐相关问题", userId, kbId);
        
        Set<String> suggestions = new LinkedHashSet<>();
        
        // 1. 找到相似的历史问题（基于关键词匹配）
        List<ChatRecord> similarQuestions = chatRecordRepository
                .findByKnowledgeBaseIdOrderByCreatedAtDesc(kbId, PageRequest.of(0, 100))
                .stream()
                .filter(record -> isSimilarQuestion(record.getQuestion(), currentQuestion))
                .limit(limit / 2)
                .collect(Collectors.toList());
        
        similarQuestions.forEach(record -> suggestions.add(record.getQuestion()));
        
        // 2. 找到同文档的其他问题
        if (suggestions.size() < limit) {
            List<ChatRecord> relatedQuestions = chatRecordRepository
                    .findByKnowledgeBaseIdOrderByCreatedAtDesc(kbId, PageRequest.of(0, 200))
                    .stream()
                    .filter(record -> !record.getQuestion().equals(currentQuestion))
                    .filter(record -> sharesCitedDocumentsWithSimilarHistory(record, currentQuestion, kbId))
                    .limit(limit - suggestions.size())
                    .collect(Collectors.toList());
            
            relatedQuestions.forEach(record -> suggestions.add(record.getQuestion()));
        }
        
        // 3. 添加热门问题
        if (suggestions.size() < limit) {
            List<String> popularQuestions = getPopularQuestions(kbId, limit - suggestions.size());
            suggestions.addAll(popularQuestions);
        }
        
        return suggestions.stream()
                .limit(limit)
                .collect(Collectors.toList());
    }

    /**
     * 获取热门文档
     */
    private List<DocumentResponse> getPopularDocuments(UUID kbId, int limit) {
        return kbDocumentRepository
                .findByKnowledgeBaseIdAndStatusOrderByCreatedAtDesc(kbId, DocumentStatus.READY, PageRequest.of(0, limit))
                .stream()
                .map(doc -> toDocumentResponse(doc, 0.0))
                .collect(Collectors.toList());
    }

    /**
     * 获取热门问题
     */
    private List<String> getPopularQuestions(UUID kbId, int limit) {
        return chatRecordRepository
                .findByKnowledgeBaseIdOrderByCreatedAtDesc(kbId, PageRequest.of(0, limit * 2))
                .stream()
                .map(ChatRecord::getQuestion)
                .distinct()
                .limit(limit)
                .collect(Collectors.toList());
    }

    /**
     * 提取关键词
     */
    private Set<String> extractKeywords(List<ChatRecord> chats) {
        Set<String> keywords = new HashSet<>();
        
        // 简单的关键词提取（可以后续升级为 NLP）
        for (ChatRecord chat : chats) {
            String question = chat.getQuestion();
            // 提取中文关键词（2-4个字符）
            String[] words = question.split("[\\s\\p{Punct}]+");
            for (String word : words) {
                if (word.length() >= 2 && word.length() <= 4) {
                    keywords.add(word);
                }
            }
        }
        
        return keywords;
    }

    /**
     * 计算文档相关性分数
     */
    private double calculateRelevanceScore(KbDocument doc, Set<String> keywords, List<ChatRecord> recentChats) {
        double score = 0.0;
        
        // 1. 关键词匹配分数
        String docContent = doc.getFilename() != null ? doc.getFilename() : "";
        for (String keyword : keywords) {
            if (docContent.contains(keyword)) {
                score += 1.0;
            }
        }
        
        // 2. 时间衰减因子（最近的文档权重更高）
        long daysSinceCreation = (System.currentTimeMillis() - doc.getCreatedAt().toEpochMilli()) / (1000 * 60 * 60 * 24);
        double timeDecay = Math.exp(-daysSinceCreation / 30.0); // 30天衰减
        score *= timeDecay;
        
        // 3. 文档长度惩罚（太长的文档可能不够聚焦）
        if (doc.getContentHash() != null) {
            // 假设文档长度信息存储在某个地方
            // 这里简化处理
        }
        
        return score;
    }

    /**
     * 判断两个问题是否相似
     */
    private boolean isSimilarQuestion(String q1, String q2) {
        if (q1.equals(q2)) {
            return false;
        }
        
        // 简单的相似度判断（可以后续升级为语义相似度）
        Set<String> words1 = new HashSet<>(Arrays.asList(q1.split("[\\s\\p{Punct}]+")));
        Set<String> words2 = new HashSet<>(Arrays.asList(q2.split("[\\s\\p{Punct}]+")));
        
        Set<String> intersection = new HashSet<>(words1);
        intersection.retainAll(words2);
        
        Set<String> union = new HashSet<>(words1);
        union.addAll(words2);
        
        double jaccard = union.isEmpty() ? 0 : (double) intersection.size() / union.size();
        return jaccard > 0.3; // Jaccard 相似度阈值
    }

    /**
     * 与「和当前问题相似」且带引用的历史问答，在 citations 中的 documentId 是否有交集。
     */
    private boolean sharesCitedDocumentsWithSimilarHistory(
            ChatRecord record, String currentQuestion, UUID kbId) {
        Set<UUID> recordDocs = citationDocumentIds(record);
        if (recordDocs.isEmpty()) {
            return false;
        }
        List<ChatRecord> pool =
                chatRecordRepository.findByKnowledgeBaseIdOrderByCreatedAtDesc(
                        kbId, PageRequest.of(0, 250));
        for (ChatRecord other : pool) {
            if (other.getId().equals(record.getId())) {
                continue;
            }
            if (!isSimilarQuestion(other.getQuestion(), currentQuestion)) {
                continue;
            }
            Set<UUID> otherDocs = citationDocumentIds(other);
            if (!otherDocs.isEmpty() && !Collections.disjoint(recordDocs, otherDocs)) {
                return true;
            }
        }
        return false;
    }

    private Set<UUID> citationDocumentIds(ChatRecord r) {
        if (r == null || r.getCitationsJson() == null || r.getCitationsJson().isBlank()) {
            return Set.of();
        }
        try {
            List<CitationDto> list =
                    objectMapper.readValue(r.getCitationsJson(), new TypeReference<List<CitationDto>>() {});
            return list.stream()
                    .map(CitationDto::documentId)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());
        } catch (Exception e) {
            return Set.of();
        }
    }

    /**
     * 转换为文档响应
     */
    private DocumentResponse toDocumentResponse(KbDocument doc, double ignoredScore) {
        return new DocumentResponse(
                doc.getId(),
                doc.getKnowledgeBase().getId(),
                doc.getFilename(),
                doc.getContentType(),
                doc.getSizeBytes(),
                doc.getStatus(),
                doc.getErrorMessage(),
                doc.getCreatedAt(),
                List.copyOf(readTagList(doc)),
                doc.getSourceUrl());
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
}

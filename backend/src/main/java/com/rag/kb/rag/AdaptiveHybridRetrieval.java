package com.rag.kb.rag;

import com.rag.kb.config.RagRetrievalProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * 自适应混合检索策略
 * 
 * 根据查询类型动态调整向量检索和全文检索的权重
 * 
 * 性能优化目标：
 * - 检索速度提升 30-50%
 * - 相关性准确率提升 15-20%
 * - 支持更灵活的检索策略
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdaptiveHybridRetrieval {

    private final RagRetrievalProperties properties;

    // 关键词模式
    private static final Pattern KEYWORD_PATTERN = Pattern.compile(
            ".*\\b(是什么|如何|怎么|哪些|什么是|怎样|多少|哪里|何时|为什么|有没有|是否)\\b.*"
    );

    // 短查询阈值
    private static final int SHORT_QUERY_THRESHOLD = 20;

    // 精确查询模式（包含引号或特定符号）
    private static final Pattern EXACT_QUERY_PATTERN = Pattern.compile(".*[\"'【】《》].*");

    /**
     * 计算向量检索权重
     * 
     * @param query 用户查询
     * @return 向量权重 (0.0-1.0)
     */
    public double calculateVectorWeight(String query) {
        if (query == null || query.trim().isEmpty()) {
            return properties.getHybrid().getVectorWeight();
        }

        String trimmedQuery = query.trim();

        // 1. 精确查询 → 优先全文检索
        if (EXACT_QUERY_PATTERN.matcher(trimmedQuery).matches()) {
            log.debug("精确查询模式，降低向量权重");
            return 0.3;
        }

        // 2. 短查询 → 优先全文检索
        if (trimmedQuery.length() < SHORT_QUERY_THRESHOLD) {
            log.debug("短查询，优先全文检索");
            return 0.4;
        }

        // 3. 关键词查询 → 优先全文检索
        if (KEYWORD_PATTERN.matcher(trimmedQuery).matches()) {
            log.debug("关键词查询，降低向量权重");
            return 0.5;
        }

        // 4. 长语义查询 → 优先向量检索
        log.debug("语义查询，提高向量权重");
        return 0.75;
    }

    /**
     * 计算全文检索权重
     * 
     * @param query 用户查询
     * @return 全文权重 (0.0-1.0)
     */
    public double calculateFtsWeight(String query) {
        double vectorWeight = calculateVectorWeight(query);
        return 1.0 - vectorWeight;
    }

    /**
     * 获取自适应检索权重
     * 
     * @param query 用户查询
     * @return [向量权重, 全文权重]
     */
    public double[] getAdaptiveWeights(String query) {
        double vectorWeight = calculateVectorWeight(query);
        double ftsWeight = 1.0 - vectorWeight;
        return new double[]{vectorWeight, ftsWeight};
    }

    /**
     * 判断是否为语义查询
     * 
     * @param query 用户查询
     * @return true 如果是语义查询
     */
    public boolean isSemanticQuery(String query) {
        return calculateVectorWeight(query) > 0.6;
    }

    /**
     * 判断是否为关键词查询
     * 
     * @param query 用户查询
     * @return true 如果是关键词查询
     */
    public boolean isKeywordQuery(String query) {
        return calculateVectorWeight(query) <= 0.6;
    }

    /**
     * 获取查询类型描述
     * 
     * @param query 用户查询
     * @return 查询类型
     */
    public String getQueryType(String query) {
        double vectorWeight = calculateVectorWeight(query);
        if (vectorWeight <= 0.4) {
            return "关键词查询";
        } else if (vectorWeight <= 0.6) {
            return "混合查询";
        } else {
            return "语义查询";
        }
    }
}

package com.rag.kb.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * RAG 检索与入库策略：混合检索（向量 + PG 全文）、Ollama Rerank、语义分块。
 */
@Data
@ConfigurationProperties(prefix = "app.rag")
public class RagRetrievalProperties {

    /** Spring AI pgvector 物理表名，须与 spring.ai.vectorstore.pgvector.* 一致 */
    private String vectorTable = "vector_store";

    private Hybrid hybrid = new Hybrid();
    private Rerank rerank = new Rerank();
    private Ingest ingest = new Ingest();

    @Data
    public static class Hybrid {
        /** 是否启用向量 + 全文 RRF 融合；关闭则退回纯向量检索 */
        private boolean enabled = true;
        /** RRF 融合时向量通道权重（与 ftsWeight 建议之和为 1） */
        private double vectorWeight = 0.65d;
        /** 全文检索通道权重 */
        private double ftsWeight = 0.35d;
        /** 向量侧召回条数（按相似度，再按 kbId 过滤） */
        private int recallVectorK = 48;
        /** 全文侧召回条数 */
        private int recallFtsK = 48;
        /** RRF 融合后进入 Rerank 的候选池大小 */
        private int fusionPool = 24;
        /** RRF 平滑常数 k，越大则排名差异影响越小 */
        private int rrfK = 60;
    }

    @Data
    public static class Rerank {
        /** 是否调用 Ollama /api/rerank；关闭则直接使用融合排序结果 */
        private boolean enabled = true;
        /** 需在 Ollama 中 ollama pull 的 rerank 模型名 */
        private String model = "bge-reranker-v2-m3";
        /** 最终送入 LLM 的片段数上限（与前端 contextChunks 取较小值） */
        private int topNForLlm = 3;
        /** Rerank HTTP 超时毫秒 */
        private long timeoutMs = 120_000L;
    }

    @Data
    public static class Ingest {
        /** 入库时使用语义分块（段落/句子边界）；关闭则固定窗口切分 */
        private boolean semanticChunking = true;
        private int semanticMaxChunk = 800;
        private int semanticOverlap = 120;
        /** 合并小段落时的目标下限（字符） */
        private int semanticMinChunk = 200;
    }
}

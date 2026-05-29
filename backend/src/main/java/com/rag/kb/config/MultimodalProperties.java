package com.rag.kb.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 多模态入库配置：当前实现为「图片 / 视频帧 → 视觉描述 → FTS 文本检索」。
 */
@Data
@ConfigurationProperties(prefix = "app.multimodal")
public class MultimodalProperties {

    /** 是否为图片类文档调用视觉模型生成描述（关闭则仅用文件名与占位文本入库） */
    private boolean imageCaptionEnabled = true;

    /** 是否为视频类文档抽帧后调用视觉模型生成摘要 */
    private boolean videoCaptionEnabled = true;

    /** 视觉提供方：auto/openai/ollama。auto 时随主 LLM 模式走；Qwen 场景建议 openai。 */
    private String visionProvider = "auto";

    /** OpenAI 兼容多模态模型名（如 qwen-vl-plus、qwen-vl-max）。 */
    private String openAiVisionModel = "qwen-vl-plus";

    /** Ollama 视觉模型名（可选本机回退）。为空则跳过视觉 API，使用占位描述。 */
    private String ollamaVisionModel = "llava";

    /** 视觉描述请求超时（毫秒） */
    private long captionTimeoutMs = 120_000L;

    /** 视觉调用失败时是否仍用占位文本完成入库，避免整图文档失败 */
    private boolean captionFallbackOnError = true;

    /** 视频抽帧依赖的 ffmpeg 可执行文件名或绝对路径 */
    private String ffmpegCommand = "ffmpeg";

    /** 单个视频最多抽取多少帧送入多模态模型 */
    private int videoMaxFrames = 6;

    /** 抽帧目标宽度，-1 表示保持原始宽度 */
    private int videoFrameWidth = 960;
}

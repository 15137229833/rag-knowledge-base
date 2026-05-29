package com.rag.kb.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rag.kb.config.LlmRuntimeMode;
import com.rag.kb.config.MultimodalProperties;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

/**
 * 为图片或视频帧生成简短中文描述，优先支持 Qwen / DashScope OpenAI 兼容接口，也可回退 Ollama。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OllamaVisionCaptionService {

    private static final String IMAGE_CAPTION_PROMPT =
            "请用简体中文简要描述这张图片中的关键信息（主体、场景、文字、数据图表要点等），便于后续语义检索。"
                    + "控制在 280 字以内，不要开场白。";

    private static final String VIDEO_CAPTION_PROMPT =
            "下面是一组按时间顺序抽取的视频关键帧。请用简体中文总结视频内容，包含主体、场景、动作过程、出现的文字/图表、可能的事件顺序。"
                    + "控制在 420 字以内，输出适合后续语义检索的摘要，不要开场白。";

    private final MultimodalProperties multimodalProperties;
    private final LlmRuntimeMode llmRuntimeMode;
    private final ObjectMapper objectMapper;

    @Value("${spring.ai.ollama.base-url:http://localhost:11434}")
    private String ollamaBaseUrl;

    @Value("${spring.ai.openai.base-url:https://dashscope.aliyuncs.com/compatible-mode/v1}")
    private String openAiBaseUrl;

    public String captionImage(byte[] imageBytes, String filename) {
        if (imageBytes == null || imageBytes.length == 0) {
            return null;
        }
        List<VisualInput> inputs = List.of(new VisualInput(imageBytes, imageFormat(filename), filename));
        return useOpenAiVision()
                ? captionByOpenAi(inputs, IMAGE_CAPTION_PROMPT, filename)
                : captionByOllama(inputs, IMAGE_CAPTION_PROMPT, filename);
    }

    public String captionVideoFrames(List<VideoFrame> frames, String filename) {
        if (frames == null || frames.isEmpty()) {
            return null;
        }
        List<VisualInput> inputs = new ArrayList<>();
        for (VideoFrame frame : frames) {
            inputs.add(new VisualInput(frame.bytes(), imageFormat(frame.filename()), frame.filename()));
        }
        return useOpenAiVision()
                ? captionByOpenAi(inputs, VIDEO_CAPTION_PROMPT, filename)
                : captionByOllama(inputs, VIDEO_CAPTION_PROMPT, filename);
    }

    private boolean useOpenAiVision() {
        String provider = multimodalProperties.getVisionProvider();
        if (provider == null || provider.isBlank() || "auto".equalsIgnoreCase(provider.strip())) {
            return llmRuntimeMode.isDashScopeStack();
        }
        return "openai".equalsIgnoreCase(provider.strip()) || "qwen".equalsIgnoreCase(provider.strip());
    }

    private String captionByOpenAi(List<VisualInput> inputs, String prompt, String filename) {
        String model = safe(multimodalProperties.getOpenAiVisionModel(), "qwen-vl-plus");
        long timeout = Math.max(5_000L, multimodalProperties.getCaptionTimeoutMs());
        RestClient client = buildClient(trimSlash(openAiBaseUrl), timeout);

        List<Map<String, Object>> content = new ArrayList<>();
        content.add(Map.of("type", "text", "text", prompt));
        for (VisualInput input : inputs) {
            String b64 = Base64.getEncoder().encodeToString(input.bytes());
            Map<String, Object> imageUrl = Map.of(
                    "url", "data:image/" + input.format() + ";base64," + b64);
            content.add(Map.of("type", "image_url", "image_url", imageUrl));
        }

        Map<String, Object> userMsg = new LinkedHashMap<>();
        userMsg.put("role", "user");
        userMsg.put("content", content);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("messages", List.of(userMsg));
        body.put("stream", false);
        body.put("max_tokens", 500);

        try {
            String raw = client.post()
                    .uri("/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(objectMapper.writeValueAsString(body))
                    .retrieve()
                    .body(String.class);
            String contentText = extractOpenAiContent(raw);
            if (contentText == null || contentText.isBlank()) {
                return null;
            }
            return contentText.strip();
        } catch (Exception e) {
            log.warn("OpenAI/Qwen 视觉描述失败 file={} model={}: {}", filename, model, e.getMessage());
            return null;
        }
    }

    private String captionByOllama(List<VisualInput> inputs, String prompt, String filename) {
        String model = multimodalProperties.getOllamaVisionModel();
        if (model == null || model.isBlank()) {
            log.debug("未配置 ollamaVisionModel，跳过视觉描述: {}", filename);
            return null;
        }
        long timeout = Math.max(5_000L, multimodalProperties.getCaptionTimeoutMs());
        RestClient client = buildClient(trimSlash(ollamaBaseUrl), timeout);

        List<String> images = new ArrayList<>();
        for (VisualInput input : inputs) {
            images.add(Base64.getEncoder().encodeToString(input.bytes()));
        }

        Map<String, Object> userMsg = new LinkedHashMap<>();
        userMsg.put("role", "user");
        userMsg.put("content", prompt);
        userMsg.put("images", images);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model.strip());
        body.put("messages", List.of(userMsg));
        body.put("stream", false);

        try {
            String raw = client.post()
                    .uri("/api/chat")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(objectMapper.writeValueAsString(body))
                    .retrieve()
                    .body(String.class);
            if (raw == null || raw.isBlank()) {
                return null;
            }
            JsonNode root = objectMapper.readTree(raw);
            String contentText = root.path("message").path("content").asText("");
            return contentText.isBlank() ? null : contentText.strip();
        } catch (Exception e) {
            log.warn("Ollama 视觉描述失败 file={} model={}: {}", filename, model, e.getMessage());
            return null;
        }
    }

    private RestClient buildClient(String baseUrl, long timeout) {
        SimpleClientHttpRequestFactory rf = new SimpleClientHttpRequestFactory();
        rf.setConnectTimeout(15_000);
        rf.setReadTimeout((int) Math.min(timeout, Integer.MAX_VALUE));
        return RestClient.builder().requestFactory(rf).baseUrl(baseUrl).build();
    }

    private String extractOpenAiContent(String raw) throws Exception {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        JsonNode root = objectMapper.readTree(raw);
        JsonNode choices = root.path("choices");
        if (!choices.isArray() || choices.isEmpty()) {
            return null;
        }
        JsonNode contentNode = choices.get(0).path("message").path("content");
        if (contentNode.isTextual()) {
            return contentNode.asText("");
        }
        if (contentNode.isArray()) {
            StringBuilder sb = new StringBuilder();
            for (JsonNode item : contentNode) {
                String text = item.path("text").asText("");
                if (!text.isBlank()) {
                    if (sb.length() > 0) {
                        sb.append('\n');
                    }
                    sb.append(text.strip());
                }
            }
            return sb.toString();
        }
        return null;
    }

    private static String imageFormat(String filename) {
        String lower = filename == null ? "" : filename.toLowerCase();
        if (lower.endsWith(".png")) {
            return "png";
        }
        if (lower.endsWith(".webp")) {
            return "webp";
        }
        if (lower.endsWith(".gif")) {
            return "gif";
        }
        return "jpeg";
    }

    private static String safe(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.strip();
    }

    private static String trimSlash(String u) {
        if (u == null || u.isBlank()) {
            return "https://dashscope.aliyuncs.com/compatible-mode/v1";
        }
        String s = u.strip();
        while (s.endsWith("/")) {
            s = s.substring(0, s.length() - 1);
        }
        return s;
    }

    private record VisualInput(byte[] bytes, String format, String filename) {}

    public record VideoFrame(byte[] bytes, String filename) {}
}

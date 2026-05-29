package com.rag.kb.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rag.kb.domain.AppConfig;
import com.rag.kb.dto.SystemDtos.ModelSettingsResponse;
import com.rag.kb.dto.SystemDtos.SaveModelSettingsRequest;
import com.rag.kb.repository.AppConfigRepository;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ModelSettingsService {

    private static final String KEY = "model.settings";

    private final AppConfigRepository appConfigRepository;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public ModelSettingsResponse get() {
        var cfg = appConfigRepository.findById(KEY).orElse(null);
        if (cfg == null) {
            return new ModelSettingsResponse(
                    "OLLAMA",
                    "llama3.2",
                    "nomic-embed-text",
                    "http://localhost:11434",
                    "",
                    0.7,
                    0.9,
                    40,
                    "PGVECTOR",
                    "postgresql://localhost:5432/ragkb",
                    null,
                    null);
        }
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> m = objectMapper.readValue(cfg.getValue(), Map.class);
            String keyMasked = mask((String) m.getOrDefault("apiKey", ""));
            return new ModelSettingsResponse(
                    str(m, "provider", "OLLAMA"),
                    str(m, "chatModel", "llama3.2"),
                    str(m, "embeddingModel", "nomic-embed-text"),
                    str(m, "apiBaseUrl", "http://localhost:11434"),
                    keyMasked,
                    dbl(m, "defaultTemperature", 0.7),
                    dbl(m, "defaultTopP", 0.9),
                    integer(m, "defaultTopK", 40),
                    str(m, "vectorDbType", "PGVECTOR"),
                    str(m, "vectorDbEndpoint", "postgresql://localhost:5432/ragkb"),
                    cfg.getUpdatedAt(),
                    cfg.getUpdatedBy());
        } catch (Exception e) {
            throw new IllegalStateException("读取模型配置失败", e);
        }
    }

    @Transactional
    public ModelSettingsResponse save(UUID userId, SaveModelSettingsRequest req) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("provider", req.provider().trim().toUpperCase());
        m.put("chatModel", req.chatModel().trim());
        m.put("embeddingModel", req.embeddingModel().trim());
        m.put("apiBaseUrl", blankToEmpty(req.apiBaseUrl()));
        m.put("apiKey", blankToEmpty(req.apiKey()));
        m.put("defaultTemperature", clamp(req.defaultTemperature(), 0.0, 2.0));
        m.put("defaultTopP", clamp(req.defaultTopP(), 0.1, 1.0));
        m.put("defaultTopK", Math.max(1, Math.min(200, req.defaultTopK())));
        m.put("vectorDbType", req.vectorDbType().trim().toUpperCase());
        m.put("vectorDbEndpoint", blankToEmpty(req.vectorDbEndpoint()));

        try {
            String json = objectMapper.writeValueAsString(m);
            AppConfig cfg = appConfigRepository.findById(KEY).orElseGet(AppConfig::new);
            cfg.setKey(KEY);
            cfg.setValue(json);
            cfg.setUpdatedBy(userId);
            cfg.setUpdatedAt(Instant.now());
            appConfigRepository.save(cfg);
            return get();
        } catch (Exception e) {
            throw new IllegalStateException("保存模型配置失败", e);
        }
    }

    private static String blankToEmpty(String s) {
        return s == null ? "" : s.trim();
    }

    private static double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }

    private static String str(Map<String, Object> m, String key, String def) {
        Object v = m.get(key);
        return v == null ? def : String.valueOf(v);
    }

    private static Double dbl(Map<String, Object> m, String key, Double def) {
        Object v = m.get(key);
        if (v == null) {
            return def;
        }
        try {
            return Double.parseDouble(String.valueOf(v));
        } catch (Exception e) {
            return def;
        }
    }

    private static Integer integer(Map<String, Object> m, String key, Integer def) {
        Object v = m.get(key);
        if (v == null) {
            return def;
        }
        try {
            return Integer.parseInt(String.valueOf(v));
        } catch (Exception e) {
            return def;
        }
    }

    private static String mask(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        String s = raw.trim();
        if (s.length() <= 8) {
            return "********";
        }
        return s.substring(0, 4) + "****" + s.substring(s.length() - 4);
    }
}

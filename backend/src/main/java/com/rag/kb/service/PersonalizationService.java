package com.rag.kb.service;

import com.rag.kb.domain.UserPreference;
import com.rag.kb.repository.UserPreferenceRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 个性化服务（不做 Redis 缓存，避免实体序列化与偏好结构变更导致个人中心 500）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PersonalizationService {

    private final UserPreferenceRepository userPreferenceRepository;

    @Transactional(readOnly = true)
    public UserPreference getUserPreference(UUID userId) {
        return userPreferenceRepository
                .findByUserId(userId)
                .orElseGet(() -> createDefaultPreference(userId));
    }

    @Transactional
    public UserPreference updateUserPreference(UUID userId, UserPreference preference) {
        UserPreference existing =
                userPreferenceRepository
                        .findByUserId(userId)
                        .orElseGet(
                                () -> {
                                    UserPreference newPref = new UserPreference();
                                    newPref.setUserId(userId);
                                    return newPref;
                                });

        existing.setAnswerStyle(preference.getAnswerStyle());
        existing.setLanguage(preference.getLanguage());
        existing.setExpertiseLevel(preference.getExpertiseLevel());
        existing.setFavoriteTopics(normalizeTopics(preference.getFavoriteTopics()));
        existing.setPreferredResponseLength(preference.getPreferredResponseLength());
        existing.setIncludeExamples(preference.getIncludeExamples());
        existing.setIncludeReferences(preference.getIncludeReferences());
        existing.setTone(preference.getTone());

        return userPreferenceRepository.save(existing);
    }

    public String buildPersonalizedPrompt(UUID userId, String question, String context) {
        UserPreference pref = getUserPreference(userId);

        StringBuilder prompt = new StringBuilder();

        prompt.append("你是一个")
                .append(getExpertiseLevelDescription(pref.getExpertiseLevel()))
                .append("的AI助手。\n");
        prompt.append("请用")
                .append(getAnswerStyleDescription(pref.getAnswerStyle()))
                .append("风格回答。\n");
        prompt.append("语气应该").append(getToneDescription(pref.getTone())).append("。\n");
        prompt.append("回答长度控制在").append(pref.getPreferredResponseLength()).append("字左右。\n");

        if (Boolean.TRUE.equals(pref.getIncludeExamples())) {
            prompt.append("请在回答中包含具体示例。\n");
        }
        if (Boolean.TRUE.equals(pref.getIncludeReferences())) {
            prompt.append("请标注信息来源。\n");
        }
        List<String> topics = pref.getFavoriteTopics();
        if (topics != null && !topics.isEmpty()) {
            prompt.append("用户关注的主题：")
                    .append(String.join("、", topics))
                    .append("\n");
        }

        prompt.append("\n参考资料：\n").append(context).append("\n");
        prompt.append("\n用户问题：").append(question).append("\n");
        return prompt.toString();
    }

    private UserPreference createDefaultPreference(UUID userId) {
        UserPreference pref = new UserPreference();
        pref.setUserId(userId);
        pref.setFavoriteTopics(new ArrayList<>());
        return pref;
    }

    private static List<String> normalizeTopics(List<String> topics) {
        if (topics == null) {
            return new ArrayList<>();
        }
        return topics.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toCollection(ArrayList::new));
    }

    private String getExpertiseLevelDescription(String level) {
        if (level == null) {
            return "专业";
        }
        return switch (level) {
            case "beginner" -> "面向初学者";
            case "intermediate" -> "面向中级用户";
            case "advanced" -> "面向进阶用户";
            case "expert" -> "面向专家";
            default -> "专业";
        };
    }

    private String getAnswerStyleDescription(String style) {
        if (style == null) {
            return "适中";
        }
        return switch (style) {
            case "concise" -> "简洁";
            case "detailed" -> "详细";
            case "conversational" -> "对话式";
            default -> "适中";
        };
    }

    private String getToneDescription(String tone) {
        if (tone == null) {
            return "专业";
        }
        return switch (tone) {
            case "professional" -> "专业";
            case "friendly" -> "友好";
            case "formal" -> "正式";
            case "casual" -> "轻松";
            default -> "专业";
        };
    }
}

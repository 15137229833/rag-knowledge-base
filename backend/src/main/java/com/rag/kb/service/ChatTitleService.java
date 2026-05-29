package com.rag.kb.service;

import com.rag.kb.domain.ChatSession;
import com.rag.kb.repository.ChatRecordRepository;
import com.rag.kb.repository.ChatSessionRepository;
import com.rag.kb.config.LlmRuntimeChatOptions;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChatTitleService {

    private final ChatSessionRepository chatSessionRepository;
    private final ChatRecordRepository chatRecordRepository;
    private final ChatClient chatClient;
    private final LlmRuntimeChatOptions llmRuntimeChatOptions;

    @Async
    @Transactional
    public void maybeGenerateTitleAsync(UUID sessionId, String question, String answer) {
        try {
            ChatSession s = chatSessionRepository.findById(sessionId).orElse(null);
            if (s == null) {
                return;
            }
            if (s.getTitle() != null && !s.getTitle().isBlank()) {
                return;
            }
            if (chatRecordRepository.countBySession_Id(sessionId) != 1) {
                return;
            }
            String q = truncate(question, 500);
            String a = truncate(answer, 800);
            var opts = llmRuntimeChatOptions.forTitle();
            String raw =
                    chatClient
                            .prompt()
                            .options(opts)
                            .system(
                                    """
                                    你是对话标题生成器。根据用户问题和助手回答，生成 5～10 个汉字的简短标题。
                                    仅输出标题本身：不要标点、引号、书名号、编号，不要用「对话」「问答」「关于」等空话。""")
                            .user("用户问题：\n" + q + "\n\n助手回答：\n" + a)
                            .call()
                            .content();
            String title = sanitizeTitle(raw, question);
            if (title.isBlank()) {
                return;
            }
            s.setTitle(truncate(title, 64));
            chatSessionRepository.save(s);
        } catch (Exception e) {
            log.warn("生成会话标题失败 sessionId={}", sessionId, e);
        }
    }

    private static String sanitizeTitle(String raw, String question) {
        if (raw == null) {
            return fallbackTitle(question);
        }
        String t = raw.strip().replaceAll("[\\n\\r\\t]+", " ");
        t = t.replaceAll("^[\"'「『【\\s]+|[\"'」』】\\s]*$", "");
        t = t.replaceAll("\\s+", "");
        int cps = t.codePointCount(0, t.length());
        if (cps > 10) {
            t = t.substring(0, t.offsetByCodePoints(0, 10));
            cps = 10;
        }
        if (cps < 2) {
            return fallbackTitle(question);
        }
        return t;
    }

    private static String fallbackTitle(String question) {
        if (question == null || question.isBlank()) {
            return "新对话";
        }
        String q = question.strip();
        int len = q.codePointCount(0, q.length());
        int take = Math.min(10, Math.max(5, len));
        return q.substring(0, q.offsetByCodePoints(0, Math.min(take, len)));
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }
}

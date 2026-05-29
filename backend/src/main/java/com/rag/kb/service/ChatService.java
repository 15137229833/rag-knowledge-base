package com.rag.kb.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rag.kb.config.AppProperties;
import com.rag.kb.domain.ChatRecord;
import com.rag.kb.domain.ChatSession;
import com.rag.kb.domain.UserPreference;
import com.rag.kb.dto.AuditDtos.PagedResponse;
import com.rag.kb.dto.ChatDtos.ChatRequest;
import com.rag.kb.dto.ChatDtos.ChatHistoryItem;
import com.rag.kb.dto.ChatDtos.ChatResponse;
import com.rag.kb.dto.ChatDtos.ChatStreamDone;
import com.rag.kb.dto.ChatDtos.CitationDto;
import com.rag.kb.dto.ChatDtos.SuggestQuestionsResponse;
import com.rag.kb.config.LlmRuntimeChatOptions;
import com.rag.kb.rag.RagRetrievalOrchestrator;
import com.rag.kb.repository.AppUserRepository;
import com.rag.kb.repository.ChatRecordRepository;
import com.rag.kb.repository.ChatSessionRepository;
import com.rag.kb.repository.KnowledgeBaseRepository;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

@Service
@RequiredArgsConstructor
public class ChatService {

    private static final int DEFAULT_USE_TOP = 5;
    private static final int RECENT_CONTEXT_ROUNDS = 3;

    private static final ExecutorService SSE_POOL = Executors.newCachedThreadPool();

    /** 流式连接保活，降低反向代理/浏览器空闲断开概率 */
    private static final ScheduledExecutorService SSE_HEARTBEAT =
            Executors.newScheduledThreadPool(
                    2,
                    r -> {
                        Thread t = new Thread(r, "sse-heartbeat");
                        t.setDaemon(true);
                        return t;
                    });

    private final RagRetrievalOrchestrator ragRetrievalOrchestrator;
    private final ChatClient chatClient;
    private final LlmRuntimeChatOptions llmRuntimeChatOptions;
    private final KnowledgeBaseService knowledgeBaseService;
    private final AuditService auditService;
    private final ChatRecordRepository chatRecordRepository;
    private final ChatSessionRepository chatSessionRepository;
    private final ChatTitleService chatTitleService;
    private final AppUserRepository appUserRepository;
    private final KnowledgeBaseRepository knowledgeBaseRepository;
    private final ObjectMapper objectMapper;
    private final AppProperties appProperties;
    private final TransactionTemplate transactionTemplate;
    private final PersonalizationService personalizationService;
    private final ConcurrentHashMap<UUID, Long> askTimeTracker = new ConcurrentHashMap<>();

    private record RagOutcome(
            int retrievedCandidates, List<Document> docs, List<CitationDto> citations, String ctxBlock) {}

    private record ResolvedChatSession(UUID sessionId, boolean freshConversation) {}

    private record ChatPersistResult(Optional<UUID> historyId, UUID sessionId, String sessionTitle) {}

    private ResolvedChatSession resolveChatSession(UUID userId, UUID kbId, ChatRequest req) {
        if (Boolean.TRUE.equals(req.newSession())) {
            return new ResolvedChatSession(null, true);
        }
        if (req.sessionId() != null) {
            chatSessionRepository
                    .findByIdAndUser_IdAndKnowledgeBase_Id(req.sessionId(), userId, kbId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "会话不存在或无权访问"));
            return new ResolvedChatSession(req.sessionId(), false);
        }
        return new ResolvedChatSession(null, true);
    }

    private void registerTitleAfterCommit(UUID sessionId, String question, String answer) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            chatTitleService.maybeGenerateTitleAsync(sessionId, question, answer);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        chatTitleService.maybeGenerateTitleAsync(sessionId, question, answer);
                    }
                });
    }

    private RagOutcome retrieve(UUID kbId, String q, int useTop) {
        var bundle = ragRetrievalOrchestrator.retrieve(kbId, q, useTop);
        int retrievedCandidates = bundle.retrievedCandidates();
        List<Document> docs = bundle.docsForLlm();
        if (docs.isEmpty()) {
            return new RagOutcome(retrievedCandidates, List.of(), List.of(), "");
        }
        StringBuilder ctx = new StringBuilder();
        int n = 1;
        for (Document d : docs) {
            ctx.append("[").append(n++).append("] ").append(d.getText()).append("\n\n");
        }
        List<CitationDto> citations = new ArrayList<>();
        for (Document d : docs) {
            citations.add(toCitation(d));
        }
        return new RagOutcome(retrievedCandidates, docs, citations, ctx.toString());
    }

    @Transactional
    public ChatResponse chat(UUID userId, ChatRequest req) {
        long t0 = System.nanoTime();
        UUID kbId = req.knowledgeBaseId();
        knowledgeBaseService.requireReadable(kbId, userId);
        String q = req.question() == null ? "" : req.question().trim();
        validateQuestion(q);
        checkRateLimit(userId);
        int useTop = resolveContextTop(req.contextChunks());
        double temperature = resolveTemperature(req.temperature());
        double topP = resolveTopP(req.topP());
        int topK = resolveTopK(req.topK());
        UserPreference pref = personalizationService.getUserPreference(userId);
        String answerStyleKey = resolveAnswerStyleKey(req, pref);
        ResolvedChatSession rs = resolveChatSession(userId, kbId, req);

        RagOutcome ro = retrieve(kbId, q, useTop);
        if (ro.docs().isEmpty()) {
            auditService.logByUserId(
                    userId, "CHAT", "KNOWLEDGE_BASE", kbId.toString(), truncate(req.question(), 400));
            return new ChatResponse(
                    "在当前知识库中未检索到与问题相关的片段，无法据此作答。",
                    List.of(),
                    ro.retrievedCandidates(),
                    0,
                    elapsedMs(t0),
                    null,
                    null);
        }

        String system = buildSystemPrompt(answerStyleKey, pref);
        String conversation = buildConversationContext(userId, kbId, rs);
        String userMsg = "对话历史：\n" + conversation + "\n\n上下文：\n" + ro.ctxBlock() + "\n问题：\n" + q;

        var opts = llmRuntimeChatOptions.forChat(temperature, topP, topK);
        String answer = chatClient
                .prompt()
                .options(opts)
                .system(system)
                .user(userMsg)
                .call()
                .content();

        ChatPersistResult pr =
                persistChatRecord(userId, kbId, q, answer, ro.citations(), rs, elapsedMs(t0));
        auditService.logByUserId(userId, "CHAT", "KNOWLEDGE_BASE", kbId.toString(), truncate(req.question(), 400));
        return new ChatResponse(
                answer,
                ro.citations(),
                ro.retrievedCandidates(),
                ro.docs().size(),
                elapsedMs(t0),
                pr.sessionId(),
                pr.sessionTitle());
    }

    /**
     * SSE：event: token / event: done。done.data 为 {@link ChatStreamDone} 的 JSON。
     */
    public SseEmitter startStream(UUID userId, ChatRequest req) {
        SseEmitter emitter = new SseEmitter(600_000L);
        emitter.onCompletion(() -> {});
        emitter.onTimeout(() -> {});
        SSE_POOL.execute(() -> runStream(userId, req, emitter));
        return emitter;
    }

    private void runStream(UUID userId, ChatRequest req, SseEmitter emitter) {
        long t0 = System.nanoTime();
        try {
            UUID kbId = req.knowledgeBaseId();
            knowledgeBaseService.requireReadable(kbId, userId);
            String q = req.question() == null ? "" : req.question().trim();
            validateQuestion(q);
            checkRateLimit(userId);
            int useTop = resolveContextTop(req.contextChunks());
            double temperature = resolveTemperature(req.temperature());
            double topP = resolveTopP(req.topP());
            int topK = resolveTopK(req.topK());
            UserPreference pref = personalizationService.getUserPreference(userId);
            String answerStyleKey = resolveAnswerStyleKey(req, pref);
            ResolvedChatSession rs = resolveChatSession(userId, kbId, req);

            RagOutcome ro = retrieve(kbId, q, useTop);
            if (ro.docs().isEmpty()) {
                auditService.logByUserId(
                        userId, "CHAT", "KNOWLEDGE_BASE", kbId.toString(), truncate(req.question(), 400));
                ChatStreamDone done = new ChatStreamDone(
                        "在当前知识库中未检索到与问题相关的片段，无法据此作答。",
                        List.of(),
                        ro.retrievedCandidates(),
                        0,
                        elapsedMs(t0),
                        null,
                        null,
                        null);
                emitter.send(SseEmitter.event().name("done").data(objectMapper.writeValueAsString(done)));
                emitter.complete();
                return;
            }

            String system = buildSystemPrompt(answerStyleKey, pref);
            String conversation = buildConversationContext(userId, kbId, rs);
            String userMsg = "对话历史：\n" + conversation + "\n\n上下文：\n" + ro.ctxBlock() + "\n问题：\n" + q;

            var opts = llmRuntimeChatOptions.forChat(temperature, topP, topK);

            Flux<String> flux = chatClient
                    .prompt()
                    .options(opts)
                    .system(system)
                    .user(userMsg)
                    .stream()
                    .content();

            AtomicBoolean closed = new AtomicBoolean(false);
            AtomicReference<Disposable> subscription = new AtomicReference<>();
            ScheduledFuture<?> heartbeat =
                    SSE_HEARTBEAT.scheduleAtFixedRate(
                            () -> {
                                if (closed.get()) {
                                    return;
                                }
                                try {
                                    emitter.send(SseEmitter.event().name("ping").data(""));
                                } catch (Exception ignored) {
                                    Disposable d = subscription.get();
                                    if (d != null && !d.isDisposed()) {
                                        d.dispose();
                                    }
                                }
                            },
                            20,
                            20,
                            TimeUnit.SECONDS);

            Runnable stopAll =
                    () -> {
                        closed.set(true);
                        heartbeat.cancel(false);
                    };

            StringBuilder acc = new StringBuilder();
            Disposable d =
                    flux.publishOn(Schedulers.boundedElastic())
                            .doOnNext(
                                    chunk -> {
                                        if (chunk == null || chunk.isEmpty()) {
                                            return;
                                        }
                                        acc.append(chunk);
                                        try {
                                            Map<String, String> tokenEvent = new HashMap<>();
                                            tokenEvent.put("c", chunk);
                                            emitter.send(
                                                    SseEmitter.event().name("token").data(tokenEvent));
                                        } catch (Exception ex) {
                                            Disposable sub = subscription.get();
                                            if (sub != null && !sub.isDisposed()) {
                                                sub.dispose();
                                            }
                                        }
                                    })
                            .doOnError(
                                    err -> {
                                        stopAll.run();
                                        Disposable x = subscription.get();
                                        if (x != null && !x.isDisposed()) {
                                            x.dispose();
                                        }
                                        emitter.completeWithError(err);
                                    })
                            .doOnComplete(
                                    () -> {
                                        stopAll.run();
                                        try {
                                            String answer = acc.toString();
                                            ChatPersistResult pr =
                                                    transactionTemplate.execute(
                                                            status ->
                                                                    persistChatRecord(
                                                                            userId,
                                                                            kbId,
                                                                            q,
                                                                            answer,
                                                                            ro.citations(),
                                                                            rs,
                                                                            elapsedMs(t0)));
                                            auditService.logByUserId(
                                                    userId,
                                                    "CHAT",
                                                    "KNOWLEDGE_BASE",
                                                    kbId.toString(),
                                                    truncate(req.question(), 400));
                                            ChatPersistResult safe = pr == null
                                                    ? new ChatPersistResult(Optional.empty(), null, null)
                                                    : pr;
                                            ChatStreamDone done =
                                                    new ChatStreamDone(
                                                            answer,
                                                            ro.citations(),
                                                            ro.retrievedCandidates(),
                                                            ro.docs().size(),
                                                            elapsedMs(t0),
                                                            safe.historyId().orElse(null),
                                                            safe.sessionId(),
                                                            safe.sessionTitle());
                                            emitter.send(
                                                    SseEmitter.event()
                                                            .name("done")
                                                            .data(objectMapper.writeValueAsString(done)));
                                            emitter.complete();
                                        } catch (Exception e) {
                                            emitter.completeWithError(e);
                                        }
                                    })
                            .subscribe();
            subscription.set(d);
            Runnable cleanup =
                    () -> {
                        stopAll.run();
                        if (!d.isDisposed()) {
                            d.dispose();
                        }
                    };
            emitter.onCompletion(cleanup);
            emitter.onTimeout(cleanup);
        } catch (ResponseStatusException e) {
            emitter.completeWithError(e);
        } catch (Exception e) {
            emitter.completeWithError(e);
        }
    }

    @Transactional(readOnly = true)
    public PagedResponse<ChatHistoryItem> history(
            UUID userId, UUID kbId, String keyword, int page, int size) {
        knowledgeBaseService.requireReadable(kbId, userId);
        String kw = keyword == null ? "" : keyword.trim();
        int p = Math.max(0, page);
        int s = Math.min(100, Math.max(1, size));
        PageRequest pageable = PageRequest.of(p, s, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<ChatRecord> rows = kw.isEmpty()
                ? chatRecordRepository.findByUser_IdAndKnowledgeBase_IdOrderByCreatedAtDesc(
                        userId, kbId, pageable)
                : chatRecordRepository.searchByUserAndKb(userId, kbId, kw, pageable);
        List<ChatHistoryItem> content = rows.getContent().stream().map(this::toHistoryItem).toList();
        return PagedResponse.of(rows, content);
    }

    @Transactional
    public void deleteHistory(UUID userId, UUID kbId, UUID historyId) {
        knowledgeBaseService.requireReadable(kbId, userId);
        ChatRecord r = chatRecordRepository
                .findByIdAndUser_Id(historyId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "历史记录不存在"));
        if (!r.getKnowledgeBase().getId().equals(kbId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "记录不属于当前知识库");
        }
        chatRecordRepository.delete(r);
    }

    @Transactional
    public void clearHistory(UUID userId, UUID kbId) {
        knowledgeBaseService.requireReadable(kbId, userId);
        chatRecordRepository.deleteByUser_IdAndKnowledgeBase_Id(userId, kbId);
    }

    @Transactional
    public void feedback(UUID userId, UUID kbId, UUID historyId, Boolean helpful, String note) {
        knowledgeBaseService.requireReadable(kbId, userId);
        ChatRecord r = chatRecordRepository
                .findByIdAndUser_Id(historyId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "历史记录不存在"));
        if (!r.getKnowledgeBase().getId().equals(kbId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "记录不属于当前知识库");
        }
        r.setHelpful(helpful);
        r.setFeedbackNote(truncate(note, 500));
        r.setFeedbackAt(java.time.Instant.now());
        chatRecordRepository.save(r);
    }

    @Transactional(readOnly = true)
    public SuggestQuestionsResponse suggestions(UUID userId, UUID kbId, String q) {
        knowledgeBaseService.requireReadable(kbId, userId);
        String keyword = q == null ? "" : q.trim().toLowerCase();
        var rows = chatRecordRepository.findRecentQuestions(userId, kbId, PageRequest.of(0, 40)).getContent();
        LinkedHashSet<String> set = new LinkedHashSet<>();
        for (String s : rows) {
            if (s == null || s.isBlank()) {
                continue;
            }
            String text = s.trim();
            if (!keyword.isBlank() && !text.toLowerCase().contains(keyword)) {
                continue;
            }
            set.add(text);
            if (set.size() >= 6) {
                break;
            }
        }
        return new SuggestQuestionsResponse(set.stream().toList());
    }

    private ChatPersistResult persistChatRecord(
            UUID userId,
            UUID kbId,
            String question,
            String answer,
            List<CitationDto> citations,
            ResolvedChatSession rs,
            long latencyMs) {
        try {
            ChatSession sessionEntity = null;
            if (rs.freshConversation()) {
                sessionEntity =
                        chatSessionRepository.save(
                                new ChatSession(
                                        appUserRepository.getReferenceById(userId),
                                        knowledgeBaseRepository.getReferenceById(kbId)));
            } else if (rs.sessionId() != null) {
                sessionEntity = chatSessionRepository.getReferenceById(rs.sessionId());
            }

            ChatRecord r = new ChatRecord();
            r.setUser(appUserRepository.getReferenceById(userId));
            r.setKnowledgeBase(knowledgeBaseRepository.getReferenceById(kbId));
            r.setSession(sessionEntity);
            r.setQuestion(truncate(question, 2000));
            r.setAnswer(truncate(answer, 8000));
            r.setCitationsJson(objectMapper.writeValueAsString(citations));
            r.setLatencyMs(latencyMs > 0 ? latencyMs : null);
            r = chatRecordRepository.save(r);

            UUID sid = sessionEntity != null ? sessionEntity.getId() : null;
            if (sid != null && chatRecordRepository.countBySession_Id(sid) == 1) {
                registerTitleAfterCommit(sid, question, answer);
            }

            String sessionTitle =
                    sid == null
                            ? null
                            : chatSessionRepository.findById(sid).map(ChatSession::getTitle).orElse(null);
            return new ChatPersistResult(Optional.of(r.getId()), sid, sessionTitle);
        } catch (Exception ignored) {
            return new ChatPersistResult(Optional.empty(), null, null);
        }
    }

    private ChatHistoryItem toHistoryItem(ChatRecord r) {
        List<CitationDto> citations = List.of();
        try {
            if (r.getCitationsJson() != null && !r.getCitationsJson().isBlank()) {
                citations = objectMapper.readValue(r.getCitationsJson(), new TypeReference<List<CitationDto>>() {});
            }
        } catch (Exception ignored) {
        }
        UUID sessionId = r.getSession() != null ? r.getSession().getId() : null;
        String sessionTitle = r.getSession() != null ? r.getSession().getTitle() : null;
        return new ChatHistoryItem(
                r.getId(),
                r.getKnowledgeBase().getId(),
                sessionId,
                sessionTitle,
                r.getQuestion(),
                r.getAnswer(),
                citations,
                r.getHelpful(),
                r.getFeedbackNote(),
                r.getCreatedAt());
    }

    private static String metaString(Map<String, Object> metadata, String key) {
        if (metadata == null || !metadata.containsKey(key) || metadata.get(key) == null) {
            return "";
        }
        return String.valueOf(metadata.get(key));
    }

    private static CitationDto toCitation(Document d) {
        Map<String, Object> m = d.getMetadata();
        UUID documentId = safeUuid(metaString(m, "documentId"));
        int chunkIndex = safeInt(metaString(m, "chunkIndex"), -1);
        Integer pageNo = parsePageNo(metaString(m, "pageNo"));
        String excerpt = truncate(d.getText(), 400);
        String sourceUrl = metaString(m, "sourceUrl");
        if (sourceUrl.isBlank()) {
            sourceUrl = null;
        }
        Integer lineStart = parseOptionalInt(metaString(m, "lineStart"));
        Integer lineEnd = parseOptionalInt(metaString(m, "lineEnd"));
        String modality = metaString(m, "modality");
        if (modality == null || modality.isBlank()) {
            modality = "text";
        }
        return new CitationDto(
                d.getId(),
                documentId,
                metaString(m, "filename"),
                chunkIndex,
                pageNo,
                excerpt,
                sourceUrl,
                lineStart,
                lineEnd,
                modality);
    }

    private static Integer parseOptionalInt(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        try {
            return Integer.parseInt(s.strip());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static UUID safeUuid(String s) {
        try {
            return s == null || s.isBlank() ? null : UUID.fromString(s);
        } catch (Exception e) {
            return null;
        }
    }

    private static int safeInt(String s, int def) {
        try {
            return Integer.parseInt(s);
        } catch (Exception e) {
            return def;
        }
    }

    private static Integer parsePageNo(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        try {
            return Integer.parseInt(s);
        } catch (Exception e) {
            return null;
        }
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }

    private void validateQuestion(String q) {
        int min = appProperties.getChat().getMinQuestionLength();
        int max = appProperties.getChat().getMaxQuestionLength();
        if (q.length() < min) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "问题太短，至少 " + min + " 个字符");
        }
        if (q.length() > max) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "问题过长，最多 " + max + " 个字符");
        }
    }

    private static long elapsedMs(long t0Nano) {
        return (System.nanoTime() - t0Nano) / 1_000_000L;
    }

    private static int resolveContextTop(Integer requested) {
        if (requested == null) {
            return DEFAULT_USE_TOP;
        }
        return Math.min(10, Math.max(1, requested));
    }

    private static double resolveTemperature(Double requested) {
        if (requested == null) {
            return 0.7;
        }
        return Math.min(1.5, Math.max(0, requested));
    }

    private static double resolveTopP(Double requested) {
        if (requested == null) {
            return 0.9;
        }
        return Math.min(1.0, Math.max(0.1, requested));
    }

    private static int resolveTopK(Integer requested) {
        if (requested == null) {
            return 40;
        }
        return Math.min(100, Math.max(1, requested));
    }

    private static String normalizeAnswerStyle(String style) {
        if (style == null || style.isBlank()) {
            return "NORMAL";
        }
        String s = style.trim().toUpperCase();
        if ("BRIEF".equals(s) || "DETAILED".equals(s)) {
            return s;
        }
        return "NORMAL";
    }

    private static String resolveAnswerStyleKey(ChatRequest req, UserPreference pref) {
        if (req.answerStyle() != null && !req.answerStyle().isBlank()) {
            return normalizeAnswerStyle(req.answerStyle());
        }
        if (pref == null || pref.getAnswerStyle() == null || pref.getAnswerStyle().isBlank()) {
            return "NORMAL";
        }
        return switch (pref.getAnswerStyle().trim().toLowerCase(Locale.ROOT)) {
            case "concise" -> "BRIEF";
            case "detailed" -> "DETAILED";
            default -> "NORMAL";
        };
    }

    private static String buildSystemPrompt(String answerStyleKey, UserPreference pref) {
        String base =
                """
                你是企业知识库问答助手。请仅根据用户提供的上下文作答；若上下文不足以回答，请明确说明「根据现有资料无法回答」。
                使用简体中文。不要编造上下文中不存在的事实。
                回答中可适当使用 Markdown：例如用 ## 作为小标题、用有序/无序列表组织内容、用 **加粗** 强调要点。
                """;
        String hint =
                switch (answerStyleKey) {
                    case "BRIEF" -> "风格：尽量精炼，优先条目化，控制篇幅。";
                    case "DETAILED" -> "风格：尽量完整与可执行，可展开解释，结构清晰。";
                    default -> "风格：平衡可读性与信息量。";
                };
        StringBuilder sb = new StringBuilder(base).append('\n').append(hint);
        if (pref != null) {
            if (pref.getTone() != null && !pref.getTone().isBlank()) {
                sb.append('\n').append("语气：").append(toneLine(pref.getTone()));
            }
            if (pref.getPreferredResponseLength() != null && pref.getPreferredResponseLength() > 0) {
                sb.append('\n')
                        .append("篇幅：尽量控制在约 ")
                        .append(pref.getPreferredResponseLength())
                        .append(" 字以内（为保证准确可适当超出）。");
            }
            if (Boolean.TRUE.equals(pref.getIncludeReferences())) {
                sb.append('\n').append("请标明与上下文引用编号或资料来源的对应关系。");
            }
            if (Boolean.TRUE.equals(pref.getIncludeExamples())) {
                sb.append('\n').append("在合适处给出简短示例。");
            }
        }
        return sb.toString();
    }

    private static String toneLine(String tone) {
        return switch (tone.trim().toLowerCase(Locale.ROOT)) {
            case "friendly" -> "友好、易懂。";
            case "formal" -> "正式、严谨。";
            case "casual" -> "轻松、口语化。";
            default -> "专业、克制。";
        };
    }

    private String buildConversationContext(UUID userId, UUID kbId, ResolvedChatSession rs) {
        if (rs.freshConversation() || rs.sessionId() == null) {
            return "（无）";
        }
        PageRequest pageable = PageRequest.of(0, RECENT_CONTEXT_ROUNDS);
        List<ChatRecord> rows =
                chatRecordRepository
                        .findByUser_IdAndKnowledgeBase_IdAndSession_IdOrderByCreatedAtDesc(
                                userId, kbId, rs.sessionId(), pageable)
                        .getContent();
        if (rows.isEmpty()) {
            return "（无）";
        }
        List<ChatRecord> ordered = new ArrayList<>(rows);
        java.util.Collections.reverse(ordered);
        StringBuilder sb = new StringBuilder();
        int n = 1;
        for (ChatRecord r : ordered) {
            sb.append("轮次").append(n++).append("\n")
                    .append("用户：").append(Objects.toString(r.getQuestion(), "")).append("\n")
                    .append("助手：").append(Objects.toString(truncate(r.getAnswer(), 400), "")).append("\n\n");
        }
        return sb.toString();
    }

    private void checkRateLimit(UUID userId) {
        long now = System.currentTimeMillis();
        long minInterval = Math.max(200, appProperties.getChat().getMinIntervalMs());
        
        // 使用 computeIfAbsent 原子操作，避免竞态条件
        Long lastTime = askTimeTracker.get(userId);
        if (lastTime != null && now - lastTime < minInterval) {
            long waitMs = minInterval - (now - lastTime);
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                "提问过于频繁，请 " + (waitMs / 1000.0) + " 秒后再试");
        }
        
        // 使用 putIfAbsent 确保原子性
        askTimeTracker.put(userId, now);
    }
}

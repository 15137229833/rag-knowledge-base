package com.rag.kb.api;

import com.rag.kb.dto.ChatDtos.ChatRequest;
import com.rag.kb.dto.ChatDtos.ChatHistoryItem;
import com.rag.kb.dto.ChatDtos.ChatResponse;
import com.rag.kb.dto.ChatDtos.ChatFeedbackRequest;
import com.rag.kb.dto.ChatDtos.SuggestQuestionsResponse;
import com.rag.kb.dto.AuditDtos.PagedResponse;
import com.rag.kb.security.SecurityUtils;
import com.rag.kb.service.ChatService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/v1/chat")
@RequiredArgsConstructor
@Tag(name = "Chat", description = "RAG 问答与历史记录接口")
public class ChatController {

    private final ChatService chatService;

    @PostMapping
    @Operation(summary = "执行一次 RAG 问答")
    public ChatResponse chat(@Valid @RequestBody ChatRequest req) {
        var u = SecurityUtils.requireCurrentUser();
        return chatService.chat(u.id(), req);
    }

    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "流式 RAG 问答（SSE：token / done）")
    public SseEmitter chatStream(@Valid @RequestBody ChatRequest req) {
        var u = SecurityUtils.requireCurrentUser();
        return chatService.startStream(u.id(), req);
    }

    @GetMapping("/history")
    @Operation(summary = "分页查询当前用户历史问答")
    public PagedResponse<ChatHistoryItem> history(
            @RequestParam("knowledgeBaseId") UUID knowledgeBaseId,
            @RequestParam(name = "keyword", required = false) String keyword,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "10") int size) {
        var u = SecurityUtils.requireCurrentUser();
        return chatService.history(u.id(), knowledgeBaseId, keyword, page, size);
    }

    @DeleteMapping("/history/{id}")
    @Operation(summary = "删除一条历史问答")
    public void deleteHistory(
            @PathVariable("id") UUID id, @RequestParam("knowledgeBaseId") UUID knowledgeBaseId) {
        var u = SecurityUtils.requireCurrentUser();
        chatService.deleteHistory(u.id(), knowledgeBaseId, id);
    }

    @DeleteMapping("/history")
    @Operation(summary = "清空当前知识库下的历史问答")
    public void clearHistory(@RequestParam("knowledgeBaseId") UUID knowledgeBaseId) {
        var u = SecurityUtils.requireCurrentUser();
        chatService.clearHistory(u.id(), knowledgeBaseId);
    }

    @PostMapping("/history/{id}/feedback")
    @Operation(summary = "对历史回答进行点赞/点踩反馈")
    public void feedback(
            @PathVariable("id") UUID id,
            @RequestParam("knowledgeBaseId") UUID knowledgeBaseId,
            @RequestBody ChatFeedbackRequest req) {
        var u = SecurityUtils.requireCurrentUser();
        chatService.feedback(u.id(), knowledgeBaseId, id, req.helpful(), req.note());
    }

    @GetMapping("/suggestions")
    @Operation(summary = "获取相关问题推荐")
    public SuggestQuestionsResponse suggestions(
            @RequestParam("knowledgeBaseId") UUID knowledgeBaseId,
            @RequestParam(name = "q", required = false) String q) {
        var u = SecurityUtils.requireCurrentUser();
        return chatService.suggestions(u.id(), knowledgeBaseId, q);
    }
}

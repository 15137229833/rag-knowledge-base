package com.rag.kb.api;

import com.rag.kb.dto.CollaborationDtos.CommentResponse;
import com.rag.kb.dto.CollaborationDtos.CreateCommentRequest;
import com.rag.kb.dto.CollaborationDtos.CreateVersionRequest;
import com.rag.kb.dto.CollaborationDtos.VersionResponse;
import com.rag.kb.security.SecurityUtils;
import com.rag.kb.service.CollaborationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/documents")
@RequiredArgsConstructor
@Tag(name = "Collaboration", description = "文档协作：评论与版本")
public class CollaborationController {

    private final CollaborationService collaborationService;

    @GetMapping("/{documentId}/comments")
    @Operation(summary = "文档评论列表")
    public List<CommentResponse> listComments(@PathVariable UUID documentId) {
        return collaborationService.listComments(documentId, SecurityUtils.getCurrentUserId());
    }

    @PostMapping("/{documentId}/comments")
    @Operation(summary = "发表评论")
    public CommentResponse addComment(
            @PathVariable UUID documentId, @Valid @RequestBody CreateCommentRequest req) {
        return collaborationService.addComment(documentId, SecurityUtils.getCurrentUserId(), req);
    }

    @GetMapping("/{documentId}/versions")
    @Operation(summary = "文档版本历史")
    public List<VersionResponse> listVersions(@PathVariable UUID documentId) {
        return collaborationService.listVersions(documentId, SecurityUtils.getCurrentUserId());
    }

    @PostMapping("/{documentId}/versions")
    @Operation(summary = "登记新版本（元数据，非文件二进制快照）")
    public VersionResponse addVersion(
            @PathVariable UUID documentId, @Valid @RequestBody CreateVersionRequest req) {
        return collaborationService.addVersion(documentId, SecurityUtils.getCurrentUserId(), req);
    }
}

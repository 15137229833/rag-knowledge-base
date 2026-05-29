package com.rag.kb.api;

import com.rag.kb.dto.KnowledgeBaseDtos.AddMemberRequest;
import com.rag.kb.dto.KnowledgeBaseDtos.CreateKbRequest;
import com.rag.kb.dto.KnowledgeBaseDtos.KbMemberResponse;
import com.rag.kb.dto.KnowledgeBaseDtos.KbResponse;
import com.rag.kb.dto.KnowledgeBaseDtos.UpdateKbRequest;
import com.rag.kb.security.SecurityUtils;
import com.rag.kb.service.KnowledgeBaseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PutMapping;

@RestController
@RequestMapping("/api/v1/knowledge-bases")
@RequiredArgsConstructor
@Tag(name = "KnowledgeBase", description = "知识库与成员管理接口")
public class KnowledgeBaseController {

    private final KnowledgeBaseService knowledgeBaseService;

    @GetMapping
    @Operation(summary = "列出当前用户可访问知识库")
    public List<KbResponse> list() {
        var u = SecurityUtils.requireCurrentUser();
        return knowledgeBaseService.listAccessible(u.id());
    }

    @GetMapping("/{id}")
    @Operation(summary = "获取知识库详情")
    public KbResponse getOne(@PathVariable("id") UUID id) {
        var u = SecurityUtils.requireCurrentUser();
        return knowledgeBaseService.getAccessible(id, u.id());
    }

    @GetMapping("/{id}/members")
    @Operation(summary = "列出知识库成员（仅所有者）")
    public List<KbMemberResponse> listMembers(@PathVariable("id") UUID id) {
        var u = SecurityUtils.requireCurrentUser();
        return knowledgeBaseService.listMembers(id, u.id());
    }

    @PostMapping
    @Operation(summary = "创建知识库")
    public KbResponse create(@Valid @RequestBody CreateKbRequest req) {
        var u = SecurityUtils.requireCurrentUser();
        return knowledgeBaseService.create(u.id(), req);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除知识库（仅所有者）")
    public void delete(@PathVariable("id") UUID id) {
        var u = SecurityUtils.requireCurrentUser();
        knowledgeBaseService.delete(id, u.id());
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新知识库基本信息（仅所有者）")
    public KbResponse update(@PathVariable("id") UUID id, @Valid @RequestBody UpdateKbRequest req) {
        var u = SecurityUtils.requireCurrentUser();
        return knowledgeBaseService.update(id, u.id(), req);
    }

    @PostMapping("/{id}/members")
    @Operation(summary = "添加或更新成员权限（仅所有者）")
    public void addMember(@PathVariable("id") UUID kbId, @Valid @RequestBody AddMemberRequest req) {
        var u = SecurityUtils.requireCurrentUser();
        knowledgeBaseService.addMember(kbId, u.id(), req.username(), req.permission());
    }

    @DeleteMapping("/{kbId}/members/{userId}")
    @Operation(summary = "移除成员（仅所有者）")
    public void removeMember(@PathVariable UUID kbId, @PathVariable UUID userId) {
        var u = SecurityUtils.requireCurrentUser();
        knowledgeBaseService.removeMember(kbId, u.id(), userId);
    }
}

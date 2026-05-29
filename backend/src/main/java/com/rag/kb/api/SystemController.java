package com.rag.kb.api;

import com.rag.kb.dto.AuditDtos.PagedResponse;
import com.rag.kb.dto.SystemDtos.ModelSettingsResponse;
import com.rag.kb.dto.SystemDtos.PromptTemplateCreateRequest;
import com.rag.kb.dto.SystemDtos.PromptTemplateItem;
import com.rag.kb.dto.SystemDtos.PromptTemplateRenderRequest;
import com.rag.kb.dto.SystemDtos.PromptTemplateRenderResponse;
import com.rag.kb.dto.SystemDtos.PromptTemplateUpdateRequest;
import com.rag.kb.dto.SystemDtos.RuntimeSummaryResponse;
import com.rag.kb.dto.SystemDtos.SaveModelSettingsRequest;
import com.rag.kb.dto.SystemDtos.SystemStatusResponse;
import com.rag.kb.security.SecurityUtils;
import com.rag.kb.service.ModelSettingsService;
import com.rag.kb.service.PromptTemplateService;
import com.rag.kb.service.RuntimeSummaryService;
import com.rag.kb.service.SystemStatusService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/system")
@RequiredArgsConstructor
@Tag(name = "System", description = "系统配置、Prompt 模板与运行状态")
public class SystemController {

    private final ModelSettingsService modelSettingsService;
    private final PromptTemplateService promptTemplateService;
    private final SystemStatusService systemStatusService;
    private final RuntimeSummaryService runtimeSummaryService;

    @GetMapping("/model-settings")
    @Operation(summary = "读取模型配置")
    @PreAuthorize("hasRole('ADMIN')")
    public ModelSettingsResponse getModelSettings() {
        return modelSettingsService.get();
    }

    @PutMapping("/model-settings")
    @Operation(summary = "保存模型配置")
    @PreAuthorize("hasRole('ADMIN')")
    public ModelSettingsResponse saveModelSettings(@Valid @RequestBody SaveModelSettingsRequest req) {
        var u = SecurityUtils.requireCurrentUser();
        return modelSettingsService.save(u.id(), req);
    }

    @GetMapping("/prompt-templates")
    @Operation(summary = "分页查询 Prompt 模板")
    public PagedResponse<PromptTemplateItem> pagePromptTemplates(
            @RequestParam(name = "keyword", required = false) String keyword,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size) {
        return promptTemplateService.page(keyword, page, size);
    }

    @PostMapping("/prompt-templates")
    @Operation(summary = "新增 Prompt 模板")
    @PreAuthorize("hasRole('ADMIN')")
    public PromptTemplateItem createPromptTemplate(@Valid @RequestBody PromptTemplateCreateRequest req) {
        var u = SecurityUtils.requireCurrentUser();
        return promptTemplateService.create(u.id(), req);
    }

    @PutMapping("/prompt-templates/{id}")
    @Operation(summary = "更新 Prompt 模板")
    @PreAuthorize("hasRole('ADMIN')")
    public PromptTemplateItem updatePromptTemplate(
            @PathVariable UUID id, @Valid @RequestBody PromptTemplateUpdateRequest req) {
        return promptTemplateService.update(id, req);
    }

    @DeleteMapping("/prompt-templates/{id}")
    @Operation(summary = "删除 Prompt 模板")
    @PreAuthorize("hasRole('ADMIN')")
    public void deletePromptTemplate(@PathVariable UUID id) {
        promptTemplateService.delete(id);
    }

    @PostMapping("/prompt-templates/render")
    @Operation(summary = "渲染模板（变量替换）")
    public PromptTemplateRenderResponse renderPrompt(@Valid @RequestBody PromptTemplateRenderRequest req) {
        return promptTemplateService.render(req);
    }

    @GetMapping("/status")
    @Operation(summary = "系统运行状态与调用统计")
    @PreAuthorize("hasRole('ADMIN')")
    public SystemStatusResponse status() {
        return systemStatusService.status();
    }

    @GetMapping("/runtime-summary")
    @Operation(summary = "当前运行时模型与检索摘要")
    @PreAuthorize("hasRole('ADMIN')")
    public RuntimeSummaryResponse runtimeSummary() {
        return runtimeSummaryService.getSummary();
    }
}

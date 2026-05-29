package com.rag.kb.api;

import com.rag.kb.dto.DocumentDtos.BatchDeleteRequest;
import com.rag.kb.dto.DocumentDtos.DocumentResponse;
import com.rag.kb.dto.DocumentDtos.ImportUrlRequest;
import com.rag.kb.dto.DocumentDtos.UpdateTagsRequest;
import com.rag.kb.security.SecurityUtils;
import com.rag.kb.service.KbDocumentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Document", description = "知识库文档管理接口")
public class KbDocumentController {

    private final KbDocumentService kbDocumentService;

    @GetMapping("/knowledge-bases/{kbId}/documents")
    @Operation(summary = "列出知识库文档（支持状态/类型/标签筛选与排序）")
    public List<DocumentResponse> list(
            @PathVariable UUID kbId,
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "ext", required = false) String ext,
            @RequestParam(name = "tag", required = false) String tag,
            @RequestParam(name = "sort", required = false, defaultValue = "DESC") String sort) {
        var u = SecurityUtils.requireCurrentUser();
        return kbDocumentService.list(kbId, u.id(), status, ext, tag, sort);
    }

    @PostMapping(value = "/knowledge-bases/{kbId}/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "上传文档并触发异步解析")
    public DocumentResponse upload(
            @PathVariable UUID kbId,
            @RequestPart("file") MultipartFile file,
            @RequestParam(defaultValue = "false") boolean overwrite) {
        var u = SecurityUtils.requireCurrentUser();
        return kbDocumentService.upload(kbId, u.id(), file, overwrite);
    }

    @PostMapping("/knowledge-bases/{kbId}/documents/import-url")
    @Operation(summary = "从公开网页抓取内容入库（可选：正文、图片、视频/嵌入链接，默认仅正文）")
    public List<DocumentResponse> importUrl(@PathVariable UUID kbId, @Valid @RequestBody ImportUrlRequest req) {
        var u = SecurityUtils.requireCurrentUser();
        return kbDocumentService.importFromUrl(kbId, u.id(), req);
    }

    @GetMapping("/documents/{id}/file")
    @Operation(summary = "下载/预览原始文件（PDF 等可直接 inline 预览）")
    public ResponseEntity<byte[]> file(@PathVariable UUID id) {
        var u = SecurityUtils.requireCurrentUser();
        return kbDocumentService.downloadFile(id, u.id());
    }

    @PatchMapping("/documents/{id}/tags")
    @Operation(summary = "更新文档标签（用于筛选与批量管理）")
    public DocumentResponse updateTags(@PathVariable UUID id, @RequestBody UpdateTagsRequest req) {
        var u = SecurityUtils.requireCurrentUser();
        return kbDocumentService.updateTags(id, u.id(), req);
    }

    @PostMapping("/documents/batch-delete")
    @Operation(summary = "批量删除文档")
    public void batchDelete(@Valid @RequestBody BatchDeleteRequest req) {
        var u = SecurityUtils.requireCurrentUser();
        kbDocumentService.batchDelete(u.id(), req);
    }

    @DeleteMapping("/documents/{id}")
    @Operation(summary = "删除文档")
    public void delete(@PathVariable UUID id) {
        var u = SecurityUtils.requireCurrentUser();
        kbDocumentService.delete(id, u.id());
    }

    @PostMapping("/documents/{id}/reindex")
    @Operation(summary = "重建文档索引")
    public DocumentResponse reindex(@PathVariable UUID id) {
        var u = SecurityUtils.requireCurrentUser();
        return kbDocumentService.reindex(id, u.id());
    }
}

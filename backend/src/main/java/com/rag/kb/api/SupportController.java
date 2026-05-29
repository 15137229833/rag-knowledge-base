package com.rag.kb.api;

import com.rag.kb.dto.AuditDtos.PagedResponse;
import com.rag.kb.dto.SystemDtos.SupportTicketDetail;
import com.rag.kb.dto.SystemDtos.SupportTicketCreateRequest;
import com.rag.kb.dto.SystemDtos.SupportAttachmentItem;
import com.rag.kb.dto.SystemDtos.SupportTicketItem;
import com.rag.kb.dto.SystemDtos.SupportTicketUpdateRequest;
import com.rag.kb.security.SecurityUtils;
import com.rag.kb.service.SupportTicketService;
import com.rag.kb.service.StorageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/v1/support")
@RequiredArgsConstructor
@Tag(name = "Support", description = "帮助与支持（反馈/工单）")
public class SupportController {

    private final SupportTicketService supportTicketService;
    private final StorageService storageService;

    @PostMapping("/tickets")
    @Operation(summary = "提交反馈/工单（登录用户）")
    public SupportTicketItem create(@Valid @RequestBody SupportTicketCreateRequest req) {
        var u = SecurityUtils.requireCurrentUser();
        return supportTicketService.create(u.id(), req);
    }

    @GetMapping("/my-tickets")
    @Operation(summary = "我的反馈/工单（分页）")
    public PagedResponse<SupportTicketItem> my(
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size) {
        var u = SecurityUtils.requireCurrentUser();
        return supportTicketService.myTickets(u.id(), page, size);
    }

    @GetMapping("/my-tickets/{id}")
    @Operation(summary = "我的工单详情（含时间线/附件）")
    public SupportTicketDetail myDetail(@PathVariable UUID id) {
        var u = SecurityUtils.requireCurrentUser();
        return supportTicketService.myDetail(u.id(), id);
    }

    @GetMapping("/tickets")
    @Operation(summary = "管理员：反馈/工单分页查询")
    @PreAuthorize("hasRole('ADMIN')")
    public PagedResponse<SupportTicketItem> adminPage(
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "keyword", required = false) String keyword,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size) {
        return supportTicketService.adminPage(status, keyword, page, size);
    }

    @GetMapping("/tickets/{id}")
    @Operation(summary = "管理员：工单详情（含时间线/附件）")
    @PreAuthorize("hasRole('ADMIN')")
    public SupportTicketDetail adminDetail(@PathVariable UUID id) {
        return supportTicketService.adminDetail(id);
    }

    @PutMapping("/tickets/{id}")
    @Operation(summary = "管理员：更新工单状态/备注")
    @PreAuthorize("hasRole('ADMIN')")
    public SupportTicketItem adminUpdate(
            @PathVariable UUID id, @Valid @RequestBody SupportTicketUpdateRequest req) {
        var u = SecurityUtils.requireCurrentUser();
        return supportTicketService.adminUpdate(u.id(), id, req);
    }

    @PostMapping(value = "/tickets/{id}/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "上传工单附件（用户本人或管理员）")
    public SupportTicketItem uploadAttachment(@PathVariable UUID id, @RequestPart("file") MultipartFile file) {
        var u = SecurityUtils.requireCurrentUser();
        boolean isAdmin = u.role().name().equals("ADMIN");
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "文件不能为空");
        }
        String filename = file.getOriginalFilename() == null ? "attachment" : file.getOriginalFilename();
        if (filename.length() > 200) filename = filename.substring(filename.length() - 200);
        UUID attId = UUID.randomUUID();
        String objectKey = "support/" + id + "/" + attId + "-" + filename.replaceAll("[\\\\/]+", "_");
        try {
            storageService.putObject(objectKey, file.getBytes(), file.getContentType());
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "上传失败: " + e.getMessage());
        }
        SupportAttachmentItem att =
                new SupportAttachmentItem(attId, filename, objectKey, file.getContentType(), file.getSize(), Instant.now());
        return supportTicketService.addAttachment(u.id(), id, att, isAdmin);
    }

    @GetMapping("/tickets/{id}/attachments/{attachmentId}")
    @Operation(summary = "下载工单附件（用户本人或管理员）")
    public ResponseEntity<byte[]> downloadAttachment(@PathVariable UUID id, @PathVariable UUID attachmentId) {
        var u = SecurityUtils.requireCurrentUser();
        boolean isAdmin = u.role().name().equals("ADMIN");
        SupportTicketDetail detail = isAdmin ? supportTicketService.adminDetail(id) : supportTicketService.myDetail(u.id(), id);
        var att = detail.ticket().attachments().stream().filter(a -> attachmentId.equals(a.id())).findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "附件不存在"));
        byte[] bytes = storageService.getObjectBytes(att.objectKey());
        String fn = URLEncoder.encode(att.filename(), StandardCharsets.UTF_8).replaceAll("\\+", "%20");
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(att.contentType() == null ? "application/octet-stream" : att.contentType()));
        headers.set(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + fn);
        return ResponseEntity.ok().headers(headers).body(bytes);
    }
}


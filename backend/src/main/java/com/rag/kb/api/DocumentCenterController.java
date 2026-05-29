package com.rag.kb.api;

import com.rag.kb.dto.SystemDtos.DocumentCenterItem;
import com.rag.kb.security.SecurityUtils;
import com.rag.kb.service.DocumentCenterService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/documents")
@RequiredArgsConstructor
@Tag(name = "DocumentCenter", description = "文档中心聚合查询接口")
public class DocumentCenterController {

    private final DocumentCenterService documentCenterService;

    @GetMapping
    @Operation(summary = "跨知识库查询文档（按名称/状态/类型/标签筛选）")
    public List<DocumentCenterItem> list(
            @RequestParam(name = "keyword", required = false) String keyword,
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "ext", required = false) String ext,
            @RequestParam(name = "tag", required = false) String tag) {
        var u = SecurityUtils.requireCurrentUser();
        return documentCenterService.list(u.id(), keyword, status, ext, tag);
    }
}

package com.rag.kb.api;

import com.rag.kb.domain.ApiToken;
import com.rag.kb.dto.ApiTokenDtos.CreateTokenRequest;
import com.rag.kb.dto.ApiTokenDtos.CreateTokenResponse;
import com.rag.kb.dto.ApiTokenDtos.TokenListItem;
import com.rag.kb.security.SecurityUtils;
import com.rag.kb.service.ApiTokenService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/user/api-tokens")
@RequiredArgsConstructor
@Tag(name = "API tokens", description = "开放平台访问令牌")
public class ApiTokenController {

    private final ApiTokenService apiTokenService;

    @GetMapping
    @Operation(summary = "列出当前用户的 Token（脱敏）")
    public List<TokenListItem> list() {
        UUID userId = SecurityUtils.getCurrentUserId();
        return apiTokenService.listUserTokens(userId).stream().map(this::toMasked).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "创建 Token（仅本次响应返回明文）")
    public CreateTokenResponse create(@Valid @RequestBody CreateTokenRequest req) {
        UUID userId = SecurityUtils.getCurrentUserId();
        ApiToken t =
                apiTokenService.createToken(
                        userId,
                        req.name().trim(),
                        req.appName() == null ? null : req.appName().trim(),
                        req.appDescription() == null ? null : req.appDescription().trim(),
                        req.expiryDays());
        return new CreateTokenResponse(
                t.getId(), t.getToken(), t.getName(), t.getAppName(), t.getExpiresAt());
    }

    @PostMapping("/{tokenId}/revoke")
    @Operation(summary = "撤销 Token")
    public void revoke(@PathVariable UUID tokenId) {
        apiTokenService.revokeToken(tokenId, SecurityUtils.getCurrentUserId());
    }

    @DeleteMapping("/{tokenId}")
    @Operation(summary = "删除 Token")
    public void delete(@PathVariable UUID tokenId) {
        apiTokenService.deleteToken(tokenId, SecurityUtils.getCurrentUserId());
    }

    private TokenListItem toMasked(ApiToken t) {
        return new TokenListItem(
                t.getId(),
                t.getName(),
                maskToken(t.getToken()),
                t.getAppName(),
                Boolean.TRUE.equals(t.getIsActive()),
                t.getExpiresAt(),
                t.getLastUsedAt(),
                t.getRateLimitPerMinute(),
                t.getCreatedAt());
    }

    private static String maskToken(String token) {
        if (token == null || token.length() < 12) {
            return "****";
        }
        return token.substring(0, 8) + "…" + token.substring(token.length() - 4);
    }
}

package com.rag.kb.service;

import com.rag.kb.domain.ApiToken;
import com.rag.kb.repository.ApiTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * API Token 管理服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ApiTokenService {

    private final ApiTokenRepository apiTokenRepository;
    private static final SecureRandom RANDOM = new SecureRandom();

    /**
     * 创建 API Token
     */
    @Transactional
    public ApiToken createToken(UUID userId, String name, String appName, String appDescription, Integer expiryDays) {
        String token = generateToken();
        
        ApiToken apiToken = new ApiToken();
        apiToken.setToken(token);
        apiToken.setName(name);
        apiToken.setUserId(userId);
        apiToken.setAppName(appName);
        apiToken.setAppDescription(appDescription);
        apiToken.setIsActive(true);
        
        if (expiryDays != null && expiryDays > 0) {
            apiToken.setExpiresAt(Instant.now().plus(expiryDays, ChronoUnit.DAYS));
        }
        
        return apiTokenRepository.save(apiToken);
    }

    /**
     * 验证 Token
     */
    @Transactional
    public Optional<ApiToken> validateToken(String token) {
        Optional<ApiToken> apiToken = apiTokenRepository.findByToken(token);
        
        if (apiToken.isPresent() && apiToken.get().isValid()) {
            // 更新最后使用时间
            if (apiToken.get().shouldUpdateLastUsed()) {
                apiToken.get().setLastUsedAt(Instant.now());
                apiTokenRepository.save(apiToken.get());
            }
            return apiToken;
        }
        
        return Optional.empty();
    }

    /**
     * 列出用户的所有 Token
     */
    @Transactional(readOnly = true)
    public List<ApiToken> listUserTokens(UUID userId) {
        return apiTokenRepository.findByUserId(userId);
    }

    /**
     * 撤销 Token
     */
    @Transactional
    public void revokeToken(UUID tokenId, UUID userId) {
        apiTokenRepository.findById(tokenId).ifPresent(token -> {
            if (token.getUserId().equals(userId)) {
                token.setIsActive(false);
                apiTokenRepository.save(token);
            }
        });
    }

    /**
     * 删除 Token
     */
    @Transactional
    public void deleteToken(UUID tokenId, UUID userId) {
        apiTokenRepository.findById(tokenId).ifPresent(token -> {
            if (token.getUserId().equals(userId)) {
                apiTokenRepository.delete(token);
            }
        });
    }

    /**
     * 生成随机 Token
     */
    private String generateToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return "rag_" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}

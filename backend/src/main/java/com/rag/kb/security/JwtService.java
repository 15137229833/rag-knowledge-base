package com.rag.kb.security;

import com.rag.kb.config.AppProperties;
import com.rag.kb.domain.AppUser;
import com.rag.kb.domain.UserRole;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class JwtService {

    private final AppProperties appProperties;

    public String generateAccessToken(AppUser user) {
        long now = System.currentTimeMillis();
        long exp = now + appProperties.getJwt().getExpirationMs();
        SecretKey key = signingKey();
        return Jwts.builder()
                .subject(user.getUsername())
                .issuedAt(new Date(now))
                .expiration(new Date(exp))
                .claim("uid", user.getId().toString())
                .claim("role", user.getRole().name())
                .signWith(key)
                .compact();
    }

    public Claims parseClaims(String token) {
        return Jwts.parser().verifyWith(signingKey()).build().parseSignedClaims(token).getPayload();
    }

    /**
     * 与 {@code .env.example} 一致：优先将配置视为 Base64；若解码失败则按 UTF-8 明文使用。
     * HS256 要求至少 32 字节密钥材料，不足时用 SHA-256 派生。
     */
    private SecretKey signingKey() {
        String configured = appProperties.getJwt().getSecret().trim();
        byte[] raw;
        try {
            raw = Decoders.BASE64.decode(configured);
        } catch (RuntimeException ignored) {
            // JJWT 对非法 Base64 抛 DecodingException（非 IllegalArgumentException），须统一回退为明文 UTF-8
            raw = configured.getBytes(StandardCharsets.UTF_8);
        }
        if (raw.length < 32) {
            raw = sha256(raw);
        }
        return Keys.hmacShaKeyFor(raw);
    }

    private static byte[] sha256(byte[] input) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(input);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public AuthenticatedUser toAuthenticatedUser(Claims claims) {
        String username = claims.getSubject();
        UUID id = UUID.fromString(claims.get("uid", String.class));
        UserRole role = UserRole.valueOf(claims.get("role", String.class));
        return new AuthenticatedUser(id, username, role);
    }
}

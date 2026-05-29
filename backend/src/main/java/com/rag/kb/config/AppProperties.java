package com.rag.kb.config;

import java.util.List;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;

@Data
@Component
@ConfigurationProperties(prefix = "app")
@Slf4j
public class AppProperties {

    private Jwt jwt = new Jwt();
    private Minio minio = new Minio();
    private Bootstrap bootstrap = new Bootstrap();
    private Chat chat = new Chat();
    /** CORS 允许的前端来源 */
    private List<String> corsAllowedOrigins = List.of("http://localhost:5173", "http://127.0.0.1:5173");

    /** 与 application.yml 中默认占位一致，仅用于启动时提示（勿用于生产） */
    private static final String DEV_DEFAULT_JWT_SECRET = "local-dev-RAG-JWT-secret-min-32-chars-ok!!";

    @PostConstruct
    public void validateConfiguration() {
        if (jwt.secret == null || jwt.secret.isBlank()) {
            log.error("JWT Secret 未配置：请设置环境变量 JWT_SECRET（至少 32 字符）");
            throw new IllegalStateException(
                    "JWT_SECRET is not set or is blank. Set env JWT_SECRET (at least 32 characters).");
        }
        if (jwt.secret.length() < 32) {
            log.warn("JWT Secret 长度不足 32 字符，建议使用更长的密钥");
        }
        if (DEV_DEFAULT_JWT_SECRET.equals(jwt.secret)) {
            log.warn(
                    "当前使用开发默认 JWT_SECRET（application.yml）。部署生产前请务必设置环境变量 JWT_SECRET 覆盖。");
        }
        
        // 生产环境检查
        if (bootstrap.enabled && "admin123456".equals(bootstrap.adminPassword)) {
            log.warn("⚠️ 使用默认管理员密码且 Bootstrap 启用！生产环境请设置 ADMIN_PASSWORD 环境变量并设 BOOTSTRAP_ENABLED=false");
        }
        
        if ("minio123456".equals(minio.secretKey)) {
            log.warn("⚠️ 使用默认 MinIO 密钥！生产环境请设置 MINIO_SECRET_KEY 环境变量");
        }
    }

    @Data
    public static class Jwt {
        private String secret;
        private long expirationMs = 86400000L;
    }

    @Data
    public static class Minio {
        private String endpoint;
        private String accessKey;
        private String secretKey;
        private String bucket;
    }

    /** 启动时确保存在系统管理员账号（本地/演示用，生产请改密或关闭） */
    @Data
    public static class Bootstrap {
        private boolean enabled = true;
        private String adminUsername = "admin";
        private String adminPassword = "admin123456";
    }

    /** 问答约束（防误触/刷接口） */
    @Data
    public static class Chat {
        private int minQuestionLength = 4;
        private int maxQuestionLength = 1000;
        private long minIntervalMs = 1200;
    }
}

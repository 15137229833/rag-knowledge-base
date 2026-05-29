package com.rag.kb.config;

import com.rag.kb.domain.AppUser;
import com.rag.kb.domain.UserRole;
import com.rag.kb.repository.AppUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@Order(100)
@RequiredArgsConstructor
@Slf4j
public class AdminBootstrapRunner implements ApplicationRunner {

    private final AppProperties appProperties;
    private final AppUserRepository appUserRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(ApplicationArguments args) {
        AppProperties.Bootstrap b = appProperties.getBootstrap();
        if (!b.isEnabled()) {
            return;
        }
        String username = b.getAdminUsername() == null ? "admin" : b.getAdminUsername().trim();
        if (username.isEmpty()) {
            return;
        }
        appUserRepository
                .findByUsername(username)
                .ifPresentOrElse(
                        u -> {
                            if (u.getRole() != UserRole.ADMIN) {
                                u.setRole(UserRole.ADMIN);
                                appUserRepository.save(u);
                                log.info("已将用户 {} 提升为 ADMIN", username);
                            }
                        },
                        () -> {
                            String rawPwd =
                                    b.getAdminPassword() != null ? b.getAdminPassword() : "admin123456";
                            AppUser u = new AppUser();
                            u.setUsername(username);
                            u.setPasswordHash(passwordEncoder.encode(rawPwd));
                            u.setRole(UserRole.ADMIN);
                            u.setEmail(null);
                            appUserRepository.save(u);
                            log.info("已创建默认管理员 {}（请在生产环境修改密码并考虑关闭 app.bootstrap）", username);
                        });
    }
}

package com.rag.kb.api;

import com.rag.kb.dto.AuthDtos.ChangePasswordRequest;
import com.rag.kb.dto.AuthDtos.UserProfile;
import com.rag.kb.repository.AppUserRepository;
import com.rag.kb.security.SecurityUtils;
import com.rag.kb.service.AuditService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final AppUserRepository appUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;

    @GetMapping("/me")
    public UserProfile me() {
        var u = SecurityUtils.requireCurrentUser();
        return new UserProfile(u.id(), u.username(), u.role().name());
    }

    @PostMapping("/me/change-password")
    public void changePassword(@Valid @RequestBody ChangePasswordRequest req) {
        var u = SecurityUtils.requireCurrentUser();
        var user = appUserRepository
                .findById(u.id())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "用户不存在"));

        if (!passwordEncoder.matches(req.oldPassword(), user.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "旧密码不正确");
        }
        if (req.newPassword().equals(req.oldPassword())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "新密码不能与旧密码相同");
        }
        user.setPasswordHash(passwordEncoder.encode(req.newPassword()));
        appUserRepository.save(user);
        auditService.logByUserId(u.id(), "CHANGE_PASSWORD", "USER", u.id().toString(), null);
    }
}

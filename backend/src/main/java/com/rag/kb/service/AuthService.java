package com.rag.kb.service;

import com.rag.kb.domain.AppUser;
import com.rag.kb.domain.UserRole;
import com.rag.kb.dto.AuthDtos.LoginRequest;
import com.rag.kb.dto.AuthDtos.RegisterRequest;
import com.rag.kb.dto.AuthDtos.TokenResponse;
import com.rag.kb.dto.AuthDtos.UserProfile;
import com.rag.kb.repository.AppUserRepository;
import com.rag.kb.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final AppUserRepository appUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final AuditService auditService;

    @Transactional
    public UserProfile register(RegisterRequest req) {
        if (appUserRepository.existsByUsername(req.username())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "用户名已存在");
        }
        AppUser user = new AppUser();
        user.setUsername(req.username().trim());
        user.setPasswordHash(passwordEncoder.encode(req.password()));
        user.setEmail(req.email() == null || req.email().isBlank() ? null : req.email().trim());
        user.setRole(UserRole.USER);
        user = appUserRepository.save(user);
        auditService.logByUserId(user.getId(), "REGISTER", "USER", user.getId().toString(), null);
        return new UserProfile(user.getId(), user.getUsername(), user.getRole().name());
    }

    @Transactional(readOnly = true)
    public TokenResponse login(LoginRequest req) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(req.username(), req.password()));
        AppUser user = appUserRepository
                .findByUsername(req.username())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        String token = jwtService.generateAccessToken(user);
        auditService.logByUserId(user.getId(), "LOGIN", "USER", user.getId().toString(), null);
        return new TokenResponse(
                token, new UserProfile(user.getId(), user.getUsername(), user.getRole().name()));
    }
}

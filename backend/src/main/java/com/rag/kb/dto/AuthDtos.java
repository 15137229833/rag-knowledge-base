package com.rag.kb.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class AuthDtos {

    private AuthDtos() {}

    public record RegisterRequest(
            @NotBlank @Size(max = 64) String username,
            @NotBlank @Size(min = 6, max = 72) String password,
            @Size(max = 128) String email) {}

    public record LoginRequest(@NotBlank String username, @NotBlank String password) {}

    public record TokenResponse(String token, UserProfile user) {}

    public record UserProfile(java.util.UUID id, String username, String role) {}

    public record ChangePasswordRequest(@NotBlank String oldPassword, @NotBlank @Size(min = 6, max = 72) String newPassword) {}
}

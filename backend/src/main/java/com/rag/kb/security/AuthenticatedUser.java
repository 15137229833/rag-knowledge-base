package com.rag.kb.security;

import com.rag.kb.domain.UserRole;
import java.io.Serializable;
import java.util.UUID;

public record AuthenticatedUser(UUID id, String username, UserRole role) implements Serializable {}

package com.rag.kb.repository;

import com.rag.kb.domain.ApiToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ApiTokenRepository extends JpaRepository<ApiToken, UUID> {
    Optional<ApiToken> findByToken(String token);
    List<ApiToken> findByUserId(UUID userId);
}

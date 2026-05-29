package com.rag.kb.repository;

import com.rag.kb.domain.KbMember;
import com.rag.kb.domain.KbMember.KbMemberId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface KbMemberRepository extends JpaRepository<KbMember, KbMemberId> {

    List<KbMember> findByKbId(UUID kbId);

    Optional<KbMember> findByKbIdAndUserId(UUID kbId, UUID userId);

    List<KbMember> findByUserId(UUID userId);
}

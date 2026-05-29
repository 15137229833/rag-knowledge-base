package com.rag.kb.service;

import com.rag.kb.config.CacheConfig;
import com.rag.kb.domain.KbMember;
import com.rag.kb.domain.KbPermission;
import com.rag.kb.domain.KnowledgeBase;
import com.rag.kb.dto.KnowledgeBaseDtos.CreateKbRequest;
import com.rag.kb.dto.KnowledgeBaseDtos.KbMemberResponse;
import com.rag.kb.dto.KnowledgeBaseDtos.KbResponse;
import com.rag.kb.dto.KnowledgeBaseDtos.UpdateKbRequest;
import com.rag.kb.repository.AppUserRepository;
import com.rag.kb.repository.KbDocumentRepository;
import com.rag.kb.repository.KbMemberRepository;
import com.rag.kb.repository.KnowledgeBaseRepository;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class KnowledgeBaseService {

    private final KnowledgeBaseRepository knowledgeBaseRepository;
    private final KbMemberRepository kbMemberRepository;
    private final KbDocumentRepository kbDocumentRepository;
    private final AppUserRepository appUserRepository;
    private final AuditService auditService;

    private KbResponse toKbResponse(KnowledgeBase kb, UUID viewerId) {
        UUID id = kb.getId();
        long docCount = kbDocumentRepository.countByKnowledgeBase_Id(id);
        Instant lastActivity = kbDocumentRepository.findMaxCreatedAtByKbId(id).orElse(null);
        return new KbResponse(
                kb.getId(),
                kb.getName(),
                kb.getDescription(),
                kb.getOwner().getId(),
                resolveRoleLabel(kb, viewerId),
                docCount,
                lastActivity);
    }

    @Transactional(readOnly = true)
    public List<KbResponse> listAccessible(UUID userId) {
        LinkedHashSet<KnowledgeBase> set = new LinkedHashSet<>();
        knowledgeBaseRepository.findByOwner_Id(userId).forEach(set::add);
        kbMemberRepository.findByUserId(userId).forEach(m -> knowledgeBaseRepository
                .findById(m.getKbId())
                .ifPresent(set::add));
        List<KbResponse> out = new ArrayList<>();
        for (KnowledgeBase kb : set) {
            out.add(toKbResponse(kb, userId));
        }
        return out;
    }

    private String resolveRoleLabel(KnowledgeBase kb, UUID userId) {
        if (kb.getOwner().getId().equals(userId)) {
            return "OWNER";
        }
        return kbMemberRepository
                .findByKbIdAndUserId(kb.getId(), userId)
                .map(m -> m.getPermission().name())
                .orElse("READ");
    }

    @CacheEvict(value = CacheConfig.KNOWLEDGE_BASE_CACHE, key = "#result.id()")
    @Transactional
    public KbResponse create(UUID ownerId, CreateKbRequest req) {
        var owner = appUserRepository
                .findById(ownerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        KnowledgeBase kb = new KnowledgeBase(req.name().trim(), req.description(), owner);
        kb = knowledgeBaseRepository.save(kb);
        auditService.logByUserId(ownerId, "KB_CREATE", "KNOWLEDGE_BASE", kb.getId().toString(), kb.getName());
        return toKbResponse(kb, ownerId);
    }

    @CacheEvict(value = CacheConfig.KNOWLEDGE_BASE_CACHE, key = "#kbId")
    @Transactional
    public KbResponse update(UUID kbId, UUID userId, UpdateKbRequest req) {
        KnowledgeBase kb = knowledgeBaseRepository
                .findById(kbId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "知识库不存在"));
        if (!kb.getOwner().getId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "仅所有者可修改知识库信息");
        }
        kb.setName(req.name().trim());
        kb.setDescription(req.description());
        kb = knowledgeBaseRepository.save(kb);
        auditService.logByUserId(userId, "KB_UPDATE", "KNOWLEDGE_BASE", kbId.toString(), kb.getName());
        return toKbResponse(kb, userId);
    }

    /**
     * 不可对 JPA 实体做 Redis JSON 缓存：LAZY 代理序列化会失败（进知识库页 500），且 key 仅 kbId 会跳过按用户的权限校验。
     */
    @Transactional(readOnly = true)
    public KnowledgeBase requireReadable(UUID kbId, UUID userId) {
        KnowledgeBase kb = knowledgeBaseRepository
                .findById(kbId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "知识库不存在"));
        if (kb.getOwner().getId().equals(userId)) {
            return kb;
        }
        if (kbMemberRepository.findByKbIdAndUserId(kbId, userId).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "无权访问该知识库");
        }
        return kb;
    }

    @Transactional(readOnly = true)
    public KbResponse getAccessible(UUID kbId, UUID userId) {
        KnowledgeBase kb = requireReadable(kbId, userId);
        return toKbResponse(kb, userId);
    }

    @Transactional(readOnly = true)
    public List<KbMemberResponse> listMembers(UUID kbId, UUID requesterId) {
        if (!isOwner(kbId, requesterId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "仅所有者可查看成员列表");
        }
        return kbMemberRepository.findByKbId(kbId).stream()
                .map(m -> {
                    var u = appUserRepository
                            .findById(m.getUserId())
                            .orElseThrow(() -> new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "成员数据不一致"));
                    return new KbMemberResponse(u.getId(), u.getUsername(), m.getPermission().name());
                })
                .sorted(Comparator.comparing(KbMemberResponse::username))
                .toList();
    }

    @Transactional(readOnly = true)
    public void requireWritable(UUID kbId, UUID userId) {
        KnowledgeBase kb = knowledgeBaseRepository
                .findById(kbId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "知识库不存在"));
        if (kb.getOwner().getId().equals(userId)) {
            return;
        }
        KbMember m = kbMemberRepository
                .findByKbIdAndUserId(kbId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "无权修改该知识库"));
        if (m.getPermission() != KbPermission.WRITE) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "需要写入权限");
        }
    }

    @Transactional(readOnly = true)
    public boolean isOwner(UUID kbId, UUID userId) {
        return knowledgeBaseRepository
                .findById(kbId)
                .map(kb -> kb.getOwner().getId().equals(userId))
                .orElse(false);
    }

    @CacheEvict(value = CacheConfig.KNOWLEDGE_BASE_CACHE, key = "#kbId")
    @Transactional
    public void delete(UUID kbId, UUID userId) {
        KnowledgeBase kb = knowledgeBaseRepository
                .findById(kbId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (!kb.getOwner().getId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "仅所有者可删除知识库");
        }
        knowledgeBaseRepository.delete(kb);
        auditService.logByUserId(userId, "KB_DELETE", "KNOWLEDGE_BASE", kbId.toString(), null);
    }

    @CacheEvict(value = CacheConfig.USER_PERMISSIONS_CACHE, key = "#kbId + ':' + *T(com.rag.kb.service.KnowledgeBaseService).getUserIdByUsername(#memberUsername)")
    @Transactional
    public void addMember(UUID kbId, UUID ownerId, String memberUsername, String permissionRaw) {
        if (!isOwner(kbId, ownerId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "仅所有者可添加成员");
        }
        var memberUser = appUserRepository
                .findByUsername(memberUsername.trim())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "用户不存在"));
        if (memberUser.getId().equals(ownerId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "不能添加所有者本人");
        }
        KbPermission perm = parsePermission(permissionRaw);
        kbMemberRepository
                .findByKbIdAndUserId(kbId, memberUser.getId())
                .ifPresentOrElse(
                        existing -> {
                            existing.setPermission(perm);
                            kbMemberRepository.save(existing);
                        },
                        () -> kbMemberRepository.save(new KbMember(kbId, memberUser.getId(), perm)));
        auditService.logByUserId(
                ownerId,
                "KB_MEMBER_ADD",
                "KNOWLEDGE_BASE",
                kbId.toString(),
                memberUsername);
    }

    private static KbPermission parsePermission(String raw) {
        if (raw == null || raw.isBlank()) {
            return KbPermission.READ;
        }
        try {
            return KbPermission.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "permission 须为 READ 或 WRITE");
        }
    }

    @CacheEvict(value = CacheConfig.USER_PERMISSIONS_CACHE, key = "#kbId + ':' + #memberUserId")
    @Transactional
    public void removeMember(UUID kbId, UUID ownerId, UUID memberUserId) {
        if (!isOwner(kbId, ownerId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "仅所有者可移除成员");
        }
        kbMemberRepository.deleteById(new KbMember.KbMemberId(kbId, memberUserId));
        auditService.logByUserId(ownerId, "KB_MEMBER_REMOVE", "KNOWLEDGE_BASE", kbId.toString(), memberUserId.toString());
    }
}

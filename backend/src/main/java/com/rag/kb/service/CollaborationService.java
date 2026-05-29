package com.rag.kb.service;

import com.rag.kb.domain.AppUser;
import com.rag.kb.domain.KbDocument;
import com.rag.kb.domain.KbDocumentComment;
import com.rag.kb.domain.KbDocumentVersion;
import com.rag.kb.dto.CollaborationDtos.CommentResponse;
import com.rag.kb.dto.CollaborationDtos.CreateCommentRequest;
import com.rag.kb.dto.CollaborationDtos.CreateVersionRequest;
import com.rag.kb.dto.CollaborationDtos.VersionResponse;
import com.rag.kb.repository.AppUserRepository;
import com.rag.kb.repository.KbDocumentCommentRepository;
import com.rag.kb.repository.KbDocumentRepository;
import com.rag.kb.repository.KbDocumentVersionRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class CollaborationService {

    private final KbDocumentRepository kbDocumentRepository;
    private final KbDocumentCommentRepository commentRepository;
    private final KbDocumentVersionRepository versionRepository;
    private final AppUserRepository appUserRepository;
    private final KnowledgeBaseService knowledgeBaseService;

    @Transactional(readOnly = true)
    public List<CommentResponse> listComments(UUID documentId, UUID userId) {
        KbDocument doc = loadDoc(documentId);
        knowledgeBaseService.requireReadable(doc.getKnowledgeBase().getId(), userId);
        return commentRepository.findByDocument_IdOrderByCreatedAtDesc(documentId).stream()
                .map(this::toCommentResponse)
                .toList();
    }

    @Transactional
    public CommentResponse addComment(UUID documentId, UUID userId, CreateCommentRequest req) {
        KbDocument doc = loadDoc(documentId);
        knowledgeBaseService.requireReadable(doc.getKnowledgeBase().getId(), userId);
        AppUser user = appUserRepository.getReferenceById(userId);
        KbDocumentComment c = new KbDocumentComment();
        c.setDocument(doc);
        c.setUser(user);
        c.setContent(req.content().trim());
        c.setPosition(req.position());
        if (req.parentCommentId() != null) {
            KbDocumentComment parent =
                    commentRepository
                            .findById(req.parentCommentId())
                            .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "父评论不存在"));
            if (!parent.getDocument().getId().equals(documentId)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "父评论不属于该文档");
            }
            c.setParent(parent);
        }
        c = commentRepository.save(c);
        return toCommentResponse(c);
    }

    @Transactional(readOnly = true)
    public List<VersionResponse> listVersions(UUID documentId, UUID userId) {
        KbDocument doc = loadDoc(documentId);
        knowledgeBaseService.requireReadable(doc.getKnowledgeBase().getId(), userId);
        return versionRepository.findByDocument_IdOrderByVersionNumberDesc(documentId).stream()
                .map(this::toVersionResponse)
                .toList();
    }

    @Transactional
    public VersionResponse addVersion(UUID documentId, UUID userId, CreateVersionRequest req) {
        KbDocument doc = loadDoc(documentId);
        UUID kbId = doc.getKnowledgeBase().getId();
        knowledgeBaseService.requireWritable(kbId, userId);
        AppUser user = appUserRepository.getReferenceById(userId);
        int next =
                versionRepository.findByDocument_IdOrderByVersionNumberDesc(documentId).stream()
                        .mapToInt(KbDocumentVersion::getVersionNumber)
                        .max()
                        .orElse(0)
                        + 1;
        KbDocumentVersion v = new KbDocumentVersion();
        v.setDocument(doc);
        v.setVersionNumber(next);
        v.setContentHash(req.contentHash().trim());
        v.setChangeLog(req.changeLog() == null ? null : req.changeLog().trim());
        v.setCreatedBy(user);
        v = versionRepository.save(v);
        return toVersionResponse(v);
    }

    private KbDocument loadDoc(UUID documentId) {
        return kbDocumentRepository
                .findById(documentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "文档不存在"));
    }

    private CommentResponse toCommentResponse(KbDocumentComment c) {
        return new CommentResponse(
                c.getId(),
                c.getDocument().getId(),
                c.getUser().getId(),
                c.getUser().getUsername(),
                c.getContent(),
                c.getPosition(),
                c.getParent() != null ? c.getParent().getId() : null,
                c.getCreatedAt());
    }

    private VersionResponse toVersionResponse(KbDocumentVersion v) {
        return new VersionResponse(
                v.getId(),
                v.getDocument().getId(),
                v.getVersionNumber(),
                v.getContentHash(),
                v.getChangeLog(),
                v.getCreatedBy().getId(),
                v.getCreatedBy().getUsername(),
                v.getCreatedAt());
    }
}

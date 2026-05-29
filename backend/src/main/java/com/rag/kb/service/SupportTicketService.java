package com.rag.kb.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rag.kb.domain.SupportTicket;
import com.rag.kb.domain.SupportTicketEvent;
import com.rag.kb.dto.AuditDtos.PagedResponse;
import com.rag.kb.dto.SystemDtos.SupportAttachmentItem;
import com.rag.kb.dto.SystemDtos.SupportTicketCreateRequest;
import com.rag.kb.dto.SystemDtos.SupportTicketDetail;
import com.rag.kb.dto.SystemDtos.SupportTicketEventItem;
import com.rag.kb.dto.SystemDtos.SupportTicketItem;
import com.rag.kb.dto.SystemDtos.SupportTicketUpdateRequest;
import com.rag.kb.repository.SupportTicketEventRepository;
import com.rag.kb.repository.SupportTicketRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SupportTicketService {

    private final SupportTicketRepository repo;
    private final SupportTicketEventRepository eventRepo;
    private final ObjectMapper objectMapper;
    private final AuditService auditService;

    private List<SupportAttachmentItem> readAttachments(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return objectMapper.readValue(json, new TypeReference<List<SupportAttachmentItem>>() {});
        } catch (Exception e) {
            return List.of();
        }
    }

    private String writeAttachments(List<SupportAttachmentItem> list) {
        try {
            return objectMapper.writeValueAsString(list == null ? List.of() : list);
        } catch (Exception e) {
            return "[]";
        }
    }

    private SupportTicketItem toItem(SupportTicket t) {
        return new SupportTicketItem(
                t.getId(),
                t.getCreatedBy(),
                t.getTopic(),
                t.getContent(),
                t.getContact(),
                t.getStatus().name(),
                t.getPriority() == null ? null : t.getPriority().name(),
                t.getAdminNote(),
                readAttachments(t.getAttachmentsJson()),
                t.getCreatedAt(),
                t.getUpdatedAt());
    }

    private void addEvent(UUID ticketId, UUID actorUserId, String type, String message, String metaJson) {
        SupportTicketEvent ev = new SupportTicketEvent();
        ev.setTicketId(ticketId);
        ev.setActorUserId(actorUserId);
        ev.setEventType(type);
        ev.setMessage(message);
        ev.setMetaJson(metaJson);
        eventRepo.save(ev);
    }

    @Transactional
    public SupportTicketItem create(UUID userId, SupportTicketCreateRequest req) {
        SupportTicket t = new SupportTicket();
        t.setCreatedBy(userId);
        t.setTopic(req.topic() == null ? null : req.topic().trim());
        t.setContent(req.content().trim());
        t.setContact(req.contact() == null ? null : req.contact().trim());
        t.setStatus(SupportTicket.Status.OPEN);
        SupportTicket.Priority pr = SupportTicket.Priority.NORMAL;
        if (req.priority() != null && !req.priority().isBlank()) {
            try {
                pr = SupportTicket.Priority.valueOf(req.priority().trim().toUpperCase(Locale.ROOT));
            } catch (Exception ignored) {
                pr = SupportTicket.Priority.NORMAL;
            }
        }
        t.setPriority(pr);
        t.setAdminNote(null);
        t.setAttachmentsJson("[]");
        t.setUpdatedAt(Instant.now());
        var saved = repo.save(t);
        addEvent(saved.getId(), userId, "CREATE", "用户提交工单", null);
        auditService.logByUserId(userId, "SUPPORT_TICKET_CREATE", "SUPPORT_TICKET", saved.getId().toString(), null);
        return toItem(saved);
    }

    public PagedResponse<SupportTicketItem> myTickets(UUID userId, int page, int size) {
        Pageable p =
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "updatedAt").and(Sort.by("createdAt")));
        var r = repo.findByCreatedByOrderByUpdatedAtDesc(userId, p);
        List<SupportTicketItem> items = r.getContent().stream().map(this::toItem).toList();
        return PagedResponse.of(r, items);
    }

    public SupportTicketDetail myDetail(UUID userId, UUID ticketId) {
        SupportTicket t =
                repo.findById(ticketId).orElseThrow(() -> new IllegalArgumentException("工单不存在或已删除"));
        if (!userId.equals(t.getCreatedBy())) {
            throw new IllegalArgumentException("无权查看该工单");
        }
        var ticket = toItem(t);
        var events =
                eventRepo.findByTicketIdOrderByCreatedAtDesc(ticketId).stream()
                        .map(ev -> new SupportTicketEventItem(ev.getId(), ev.getActorUserId(), ev.getEventType(), ev.getMessage(), ev.getCreatedAt()))
                        .toList();
        return new SupportTicketDetail(ticket, events);
    }

    public SupportTicketDetail adminDetail(UUID ticketId) {
        SupportTicket t =
                repo.findById(ticketId).orElseThrow(() -> new IllegalArgumentException("工单不存在或已删除"));
        var ticket = toItem(t);
        var events =
                eventRepo.findByTicketIdOrderByCreatedAtDesc(ticketId).stream()
                        .map(ev -> new SupportTicketEventItem(ev.getId(), ev.getActorUserId(), ev.getEventType(), ev.getMessage(), ev.getCreatedAt()))
                        .toList();
        return new SupportTicketDetail(ticket, events);
    }

    public PagedResponse<SupportTicketItem> adminPage(String status, String keyword, int page, int size) {
        Pageable p = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "updatedAt"));
        var r = repo.findAll(p);
        var content = r.getContent().stream();

        if (status != null && !status.isBlank()) {
            String s = status.trim().toUpperCase(Locale.ROOT);
            content = content.filter(t -> t.getStatus() != null && t.getStatus().name().equals(s));
        }
        if (keyword != null && !keyword.isBlank()) {
            String k = keyword.trim().toLowerCase(Locale.ROOT);
            content =
                    content.filter(
                            t ->
                                    (t.getTopic() != null && t.getTopic().toLowerCase(Locale.ROOT).contains(k))
                                            || (t.getContent() != null
                                                    && t.getContent().toLowerCase(Locale.ROOT).contains(k))
                                            || (t.getContact() != null
                                                    && t.getContact().toLowerCase(Locale.ROOT).contains(k)));
        }

        List<SupportTicketItem> items = content.map(this::toItem).toList();
        return new PagedResponse<>(items, r.getTotalElements(), r.getTotalPages(), r.getNumber(), r.getSize());
    }

    @Transactional
    public SupportTicketItem adminUpdate(UUID adminUserId, UUID id, SupportTicketUpdateRequest req) {
        SupportTicket t =
                repo.findById(id).orElseThrow(() -> new IllegalArgumentException("工单不存在或已删除"));
        SupportTicket.Status st =
                SupportTicket.Status.valueOf(req.status().trim().toUpperCase(Locale.ROOT));
        t.setStatus(st);
        t.setAdminNote(req.adminNote());
        t.setUpdatedAt(Instant.now());
        var saved = repo.save(t);
        addEvent(saved.getId(), adminUserId, "ADMIN_UPDATE", "管理员更新状态为 " + st.name(), null);
        auditService.logByUserId(adminUserId, "SUPPORT_TICKET_UPDATE", "SUPPORT_TICKET", saved.getId().toString(), null);
        return toItem(saved);
    }

    @Transactional
    public SupportTicketItem addAttachment(UUID actorUserId, UUID ticketId, SupportAttachmentItem att, boolean isAdmin) {
        SupportTicket t =
                repo.findById(ticketId).orElseThrow(() -> new IllegalArgumentException("工单不存在或已删除"));
        if (!isAdmin && !actorUserId.equals(t.getCreatedBy())) {
            throw new IllegalArgumentException("无权为该工单上传附件");
        }
        List<SupportAttachmentItem> list = new ArrayList<>(readAttachments(t.getAttachmentsJson()));
        list.add(att);
        t.setAttachmentsJson(writeAttachments(list));
        t.setUpdatedAt(Instant.now());
        var saved = repo.save(t);
        addEvent(saved.getId(), actorUserId, "ATTACHMENT_ADD", "上传附件：" + att.filename(), null);
        auditService.logByUserId(actorUserId, "SUPPORT_TICKET_ATTACH", "SUPPORT_TICKET", saved.getId().toString(), null);
        return toItem(saved);
    }
}


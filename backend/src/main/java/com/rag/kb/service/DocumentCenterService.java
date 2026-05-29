package com.rag.kb.service;

import com.rag.kb.dto.SystemDtos.DocumentCenterItem;
import com.rag.kb.repository.KbDocumentRepository;
import com.rag.kb.repository.KnowledgeBaseRepository;
import com.rag.kb.repository.KbMemberRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DocumentCenterService {

    private final KnowledgeBaseRepository knowledgeBaseRepository;
    private final KbMemberRepository kbMemberRepository;
    private final KbDocumentRepository kbDocumentRepository;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public List<DocumentCenterItem> list(UUID userId, String keyword, String status, String ext, String tag) {
        LinkedHashSet<UUID> kbIds = new LinkedHashSet<>();
        knowledgeBaseRepository.findByOwner_Id(userId).forEach(k -> kbIds.add(k.getId()));
        kbMemberRepository.findByUserId(userId).forEach(m -> kbIds.add(m.getKbId()));

        String kw = keyword == null ? "" : keyword.trim().toLowerCase(Locale.ROOT);
        String st = status == null ? "" : status.trim().toUpperCase(Locale.ROOT);
        String ex = ext == null ? "" : ext.trim().toLowerCase(Locale.ROOT);
        String tg = tag == null ? "" : tag.trim().toLowerCase(Locale.ROOT);

        List<DocumentCenterItem> out = new ArrayList<>();
        for (UUID kbId : kbIds) {
            var kbOpt = knowledgeBaseRepository.findById(kbId);
            if (kbOpt.isEmpty()) continue;
            var kb = kbOpt.get();
            var docs = kbDocumentRepository.findByKnowledgeBase_IdOrderByCreatedAtDesc(kbId);
            for (var d : docs) {
                String fn = d.getFilename() == null ? "" : d.getFilename();
                if (!kw.isBlank() && !fn.toLowerCase(Locale.ROOT).contains(kw)) continue;
                if (!st.isBlank() && !d.getStatus().name().equals(st)) continue;
                if (!ex.isBlank()) {
                    String suffix = ex.startsWith(".") ? ex : ("." + ex);
                    if (!fn.toLowerCase(Locale.ROOT).endsWith(suffix)) continue;
                }
                List<String> tags = parseTags(d.getTags());
                if (!tg.isBlank()) {
                    boolean ok = false;
                    for (String t : tags) {
                        if (t.toLowerCase(Locale.ROOT).contains(tg)) {
                            ok = true;
                            break;
                        }
                    }
                    if (!ok) continue;
                }
                out.add(new DocumentCenterItem(
                        d.getId(),
                        kbId,
                        kb.getName(),
                        d.getFilename(),
                        d.getContentType(),
                        d.getSizeBytes(),
                        d.getStatus().name(),
                        d.getErrorMessage(),
                        d.getCreatedAt(),
                        tags,
                        d.getSourceUrl()));
            }
        }
        return out;
    }

    private List<String> parseTags(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        try {
            List<String> tags = objectMapper.readValue(raw, new TypeReference<List<String>>() {});
            return tags == null ? List.of() : tags;
        } catch (Exception e) {
            return List.of();
        }
    }
}

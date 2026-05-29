package com.rag.kb.service;

import com.rag.kb.domain.PromptTemplate;
import com.rag.kb.dto.AuditDtos.PagedResponse;
import com.rag.kb.dto.SystemDtos.PromptTemplateCreateRequest;
import com.rag.kb.dto.SystemDtos.PromptTemplateItem;
import com.rag.kb.dto.SystemDtos.PromptTemplateRenderRequest;
import com.rag.kb.dto.SystemDtos.PromptTemplateRenderResponse;
import com.rag.kb.dto.SystemDtos.PromptTemplateUpdateRequest;
import com.rag.kb.repository.PromptTemplateRepository;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class PromptTemplateService {

    private final PromptTemplateRepository promptTemplateRepository;

    @Transactional(readOnly = true)
    public PagedResponse<PromptTemplateItem> page(String keyword, int page, int size) {
        int p = Math.max(0, page);
        int s = Math.min(100, Math.max(1, size));
        PageRequest pageable = PageRequest.of(p, s, Sort.by(Sort.Direction.DESC, "updatedAt"));
        String kw = keyword == null ? "" : keyword.trim();
        Page<PromptTemplate> rows = kw.isBlank()
                ? promptTemplateRepository.findAll(pageable)
                : promptTemplateRepository.findByNameContainingIgnoreCase(kw, pageable);
        return PagedResponse.of(rows, rows.getContent().stream().map(this::toItem).toList());
    }

    @Transactional
    public PromptTemplateItem create(UUID userId, PromptTemplateCreateRequest req) {
        String name = req.name().trim();
        promptTemplateRepository.findByName(name).ifPresent(x -> {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "模板名称已存在");
        });
        PromptTemplate t = new PromptTemplate();
        t.setName(name);
        t.setDescription(trimNullable(req.description()));
        t.setTemplateText(req.templateText().trim());
        t.setEnabled(req.enabled() == null || req.enabled());
        t.setCreatedBy(userId);
        t.setUpdatedAt(Instant.now());
        t = promptTemplateRepository.save(t);
        return toItem(t);
    }

    @Transactional
    public PromptTemplateItem update(UUID id, PromptTemplateUpdateRequest req) {
        PromptTemplate t = promptTemplateRepository
                .findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "模板不存在"));
        String name = req.name().trim();
        promptTemplateRepository.findByName(name).ifPresent(existing -> {
            if (!existing.getId().equals(id)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "模板名称已存在");
            }
        });
        t.setName(name);
        t.setDescription(trimNullable(req.description()));
        t.setTemplateText(req.templateText().trim());
        t.setEnabled(req.enabled() == null || req.enabled());
        t.setUpdatedAt(Instant.now());
        t = promptTemplateRepository.save(t);
        return toItem(t);
    }

    @Transactional
    public void delete(UUID id) {
        if (!promptTemplateRepository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "模板不存在");
        }
        promptTemplateRepository.deleteById(id);
    }

    @Transactional(readOnly = true)
    public PromptTemplateRenderResponse render(PromptTemplateRenderRequest req) {
        String out = req.templateText();
        Map<String, String> vars = req.variables();
        if (vars != null) {
            for (Map.Entry<String, String> e : vars.entrySet()) {
                String key = e.getKey() == null ? "" : e.getKey().trim();
                if (key.isBlank()) {
                    continue;
                }
                String value = e.getValue() == null ? "" : e.getValue();
                out = out.replace("{{" + key + "}}", value);
            }
        }
        return new PromptTemplateRenderResponse(out);
    }

    private PromptTemplateItem toItem(PromptTemplate t) {
        return new PromptTemplateItem(
                t.getId(),
                t.getName(),
                t.getDescription(),
                t.getTemplateText(),
                t.isEnabled(),
                t.getCreatedAt(),
                t.getUpdatedAt(),
                t.getCreatedBy());
    }

    private static String trimNullable(String s) {
        if (s == null) return null;
        String x = s.trim();
        return x.isEmpty() ? null : x;
    }
}

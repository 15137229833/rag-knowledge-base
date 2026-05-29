package com.rag.kb.api;

import com.rag.kb.domain.UserPreference;
import com.rag.kb.security.SecurityUtils;
import com.rag.kb.service.PersonalizationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * 个性化设置 API
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class PersonalizationController {

    private final PersonalizationService personalizationService;

    /**
     * 获取用户偏好
     */
    @GetMapping("/user/preferences")
    public ResponseEntity<UserPreference> getUserPreference() {
        UUID userId = SecurityUtils.getCurrentUserId();
        UserPreference preference = personalizationService.getUserPreference(userId);
        return ResponseEntity.ok(preference);
    }

    /**
     * 更新用户偏好
     */
    @PutMapping("/user/preferences")
    public ResponseEntity<UserPreference> updateUserPreference(@RequestBody UserPreference preference) {
        UUID userId = SecurityUtils.getCurrentUserId();
        UserPreference updated = personalizationService.updateUserPreference(userId, preference);
        return ResponseEntity.ok(updated);
    }
}

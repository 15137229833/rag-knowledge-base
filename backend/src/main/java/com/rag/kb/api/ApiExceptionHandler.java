package com.rag.kb.api;

import java.util.LinkedHashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
@Slf4j
public class ApiExceptionHandler {

    private static Map<String, Object> body(String error, String code) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("error", error);
        m.put("code", code);
        return m;
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> handleStatus(ResponseStatusException ex) {
        String msg = ex.getReason() != null ? ex.getReason() : "请求错误";
        HttpStatus status = HttpStatus.valueOf(ex.getStatusCode().value());
        String code = status.name();
        return ResponseEntity.status(ex.getStatusCode()).body(body(msg, code));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> handleUnreadableJson(HttpMessageNotReadableException ex) {
        log.debug("Malformed JSON body: {}", ex.getMessage());
        return ResponseEntity.badRequest()
                .body(body("请求体不是合法 JSON，请检查 Content-Type 与字段格式", "BAD_REQUEST"));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        StringBuilder sb = new StringBuilder();
        for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
            sb.append(fe.getField()).append(": ").append(fe.getDefaultMessage()).append("; ");
        }
        return ResponseEntity.badRequest().body(body(sb.toString().trim(), "VALIDATION_ERROR"));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> handleAccessDenied(AccessDeniedException ex) {
        log.debug("Access denied: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(body("没有权限执行该操作", "FORBIDDEN"));
    }

    /**
     * 登录失败（BadCredentials、用户不存在等）否则会落入 {@link #handleGeneric}，被误报为 500。
     */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<Map<String, Object>> handleAuthentication(AuthenticationException ex) {
        log.debug("Authentication failed: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(body("用户名或密码错误", "UNAUTHORIZED"));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException ex) {
        return ResponseEntity.badRequest()
                .body(body(ex.getMessage() != null ? ex.getMessage() : "参数错误", "BAD_REQUEST"));
    }

    /**
     * {@link com.rag.kb.security.SecurityUtils#requireCurrentUser()} 在未登录或 principal 非 {@link
     * com.rag.kb.security.AuthenticatedUser} 时抛出；历史上匿名认证放行会导致此处 500，兜底为 401。
     */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalState(IllegalStateException ex) {
        String msg = ex.getMessage() != null ? ex.getMessage() : "";
        if (msg.contains("未登录")) {
            log.debug("Require auth: {}", msg);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(body("登录已失效或无效，请重新登录", "UNAUTHORIZED"));
        }
        log.warn("IllegalStateException", ex);
        return ResponseEntity.internalServerError().body(body("服务器内部错误", "INTERNAL_ERROR"));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, Object>> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        String name = ex.getName() != null ? ex.getName() : "参数";
        String msg = name + " 格式不正确，请使用合法的时间或数值";
        if (ex.getRequiredType() != null) {
            msg = name + " 需为 " + ex.getRequiredType().getSimpleName() + " 可解析格式（如 ISO-8601 时间）";
        }
        log.debug("Type mismatch: {}", ex.getMessage());
        return ResponseEntity.badRequest().body(body(msg, "BAD_REQUEST"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneric(Exception ex) {
        log.warn("Unhandled error", ex);
        return ResponseEntity.internalServerError().body(body("服务器内部错误", "INTERNAL_ERROR"));
    }
}

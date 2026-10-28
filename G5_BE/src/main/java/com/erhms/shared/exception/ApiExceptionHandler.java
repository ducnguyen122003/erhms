package com.erhms.shared.exception;

import com.erhms.role.InvalidRoleException;
import com.erhms.user.recovery.PasswordResetException;
import com.erhms.user.security.SessionExpiredException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(PasswordResetException.class)
    ResponseEntity<?> passwordReset(PasswordResetException exception) {
        var body = new LinkedHashMap<String, Object>();
        body.put("message", exception.getMessage());
        body.put("code", exception.code);
        if (exception.retryAfterSeconds > 0 || exception.status.value() == 429) body.put("retryAfterSeconds", exception.retryAfterSeconds);
        if (!exception.fields.isEmpty()) body.put("fields", exception.fields);
        var response = ResponseEntity.status(exception.status);
        if (exception.retryAfterSeconds > 0) response.header("Retry-After", String.valueOf(exception.retryAfterSeconds));
        return response.body(body);
    }

    @ExceptionHandler(SessionExpiredException.class)
    ResponseEntity<?> sessionExpired(SessionExpiredException exception, HttpServletRequest request) {
        SecurityContextHolder.clearContext();
        var session = request.getSession(false);
        if (session != null) session.invalidate();
        return ResponseEntity.status(401).body(Map.of("message", exception.getMessage(), "code", "SESSION_EXPIRED"));
    }

    @ExceptionHandler(InvalidRoleException.class)
    ResponseEntity<?> invalidRole(InvalidRoleException exception) {
        return ResponseEntity.status(403).body(Map.of("message", exception.getMessage(), "code", "INVALID_ROLE"));
    }

    @ExceptionHandler(BusinessException.class)
    ResponseEntity<?> business(BusinessException exception) {
        return ResponseEntity.status(exception.status).body(Map.of("message", exception.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<?> invalid(MethodArgumentNotValidException exception) {
        var fields = new LinkedHashMap<String, String>();
        exception.getBindingResult().getFieldErrors()
                .forEach(field -> fields.putIfAbsent(field.getField(), field.getDefaultMessage()));
        return ResponseEntity.badRequest()
                .body(Map.of("message", "Vui lòng kiểm tra thông tin nhập", "fields", fields));
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    ResponseEntity<?> malformed(Exception exception) {
        return ResponseEntity.badRequest().body(Map.of("message", "Dữ liệu không đúng định dạng"));
    }

    @ExceptionHandler({OptimisticLockingFailureException.class, PessimisticLockingFailureException.class})
    ResponseEntity<?> conflict(Exception exception) {
        return ResponseEntity.status(409)
                .body(Map.of("message", "Dữ liệu đang được cập nhật. Hãy tải lại trước khi thử lại."));
    }
}

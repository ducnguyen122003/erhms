package com.erhms.admin.exception;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class AdminAccountExceptionHandler {
    @ExceptionHandler(AdminAccountException.class)
    ResponseEntity<?> account(AdminAccountException exception) {
        return ResponseEntity.status(exception.status)
                .body(Map.of("message", exception.getMessage(), "fields", exception.fields));
    }
}

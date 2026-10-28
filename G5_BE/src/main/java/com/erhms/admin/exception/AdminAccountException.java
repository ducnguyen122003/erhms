package com.erhms.admin.exception;

import org.springframework.http.HttpStatus;

import java.util.Map;

public class AdminAccountException extends RuntimeException {
    public final HttpStatus status;
    public final Map<String, String> fields;

    public AdminAccountException(HttpStatus status, String message, String field) {
        super(message);
        this.status = status;
        this.fields = field == null ? Map.of() : Map.of(field, message);
    }
}

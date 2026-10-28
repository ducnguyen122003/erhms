package com.erhms.user.recovery;

import org.springframework.http.HttpStatus;
import java.util.Map;

public final class PasswordResetException extends RuntimeException {
    public final HttpStatus status;
    public final String code;
    public final long retryAfterSeconds;
    public final Map<String, String> fields;

    public PasswordResetException(HttpStatus status, String code, String message, long retryAfterSeconds) {
        this(status, code, message, retryAfterSeconds, Map.of());
    }

    public PasswordResetException(HttpStatus status, String code, String message, long retryAfterSeconds, Map<String, String> fields) {
        super(message);
        this.status = status;
        this.code = code;
        this.retryAfterSeconds = retryAfterSeconds;
        this.fields = fields;
    }
}

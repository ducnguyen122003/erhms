package com.erhms.workspace;

import org.springframework.http.HttpStatus;
import java.util.Map;

public class WorkflowException extends RuntimeException {
    public final HttpStatus status;
    public final String code;
    public final Map<String,String> fields;
    public WorkflowException(HttpStatus status,String code,String message,Map<String,String> fields) {
        super(message);this.status=status;this.code=code;this.fields=fields;
    }
}

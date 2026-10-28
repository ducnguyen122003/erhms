package com.erhms.role;

import org.springframework.security.access.AccessDeniedException;

public final class InvalidRoleException extends AccessDeniedException {
    public static final String MESSAGE = "Vai trò không hợp lệ. Tài khoản phải có role_id từ 1 đến 7 và mã role tương ứng.";

    public InvalidRoleException() {
        super(MESSAGE);
    }
}

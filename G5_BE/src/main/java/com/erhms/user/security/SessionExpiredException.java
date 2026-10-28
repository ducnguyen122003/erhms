package com.erhms.user.security;

import org.springframework.security.access.AccessDeniedException;

public final class SessionExpiredException extends AccessDeniedException {
    public SessionExpiredException() { super("Thông tin tài khoản đã thay đổi. Vui lòng đăng nhập lại."); }
}

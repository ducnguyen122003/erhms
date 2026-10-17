package com.erhms.user.service;

import com.erhms.user.entity.User;
import com.erhms.user.repository.UserRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
public class CurrentUser {
    private final UserRepository users;

    public CurrentUser(UserRepository users) {
        this.users = users;
    }

    public User get() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            throw new AccessDeniedException("Vui lòng đăng nhập");
        }
        return users.findByEmail(authentication.getName())
                .orElseThrow(() -> new AccessDeniedException("Tài khoản không hợp lệ"));
    }
}

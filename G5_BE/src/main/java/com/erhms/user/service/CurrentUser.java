package com.erhms.user.service;

import com.erhms.user.entity.User;
import com.erhms.user.repository.UserRepository;
import com.erhms.user.security.UserPrincipal;
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
        return lookup(false);
    }

    public User getForUpdate() {
        return lookup(true);
    }

    private User lookup(boolean forUpdate) {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            throw new AccessDeniedException("Vui lòng đăng nhập");
        }
        var user = authentication.getPrincipal() instanceof UserPrincipal principal
                ? (forUpdate ? users.findForUpdateById(principal.getUserId())
                             : users.findWithRoleById(principal.getUserId()))
                : (forUpdate ? users.findForUpdateByEmail(authentication.getName())
                             : users.findByEmail(authentication.getName()));
        return user
                .orElseThrow(() -> new AccessDeniedException("Tài khoản không hợp lệ"));
    }
}

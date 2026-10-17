package com.erhms.user.security;

import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.userdetails.User;

public final class UserPrincipal extends User {
    private static final long serialVersionUID = 1L;

    private final Long userId;

    public UserPrincipal(Long userId, String email, String password, String roleCode) {
        super(email, password, AuthorityUtils.createAuthorityList("ROLE_" + roleCode));
        this.userId = userId;
    }

    public Long getUserId() {
        return userId;
    }
}

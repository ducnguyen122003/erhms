package com.erhms.user.security;

import com.erhms.role.DashboardRole;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.userdetails.User;

public final class UserPrincipal extends User {
    private static final long serialVersionUID = 1L;

    private final Long userId;
    private final Long roleId;
    private final String roleCode;
    private final long credentialsVersion;

    public UserPrincipal(Long userId, String email, String password, Long roleId, String roleCode, boolean enabled, boolean locked, long credentialsVersion) {
        super(email, password, enabled, true, true, !locked, authorities(roleId, roleCode));
        this.userId = userId;
        this.roleId = roleId;
        this.roleCode = roleCode;
        this.credentialsVersion = credentialsVersion;
    }

    private static java.util.List<org.springframework.security.core.GrantedAuthority> authorities(Long roleId, String code) {
        return DashboardRole.resolve(roleId, code)
                .map(role -> AuthorityUtils.createAuthorityList("ROLE_" + role.name()))
                .orElseGet(java.util.List::of);
    }

    public boolean hasSupportedRole() {
        return DashboardRole.resolve(roleId, roleCode).isPresent();
    }

    public Long getUserId() {
        return userId;
    }

    public long getCredentialsVersion() { return credentialsVersion; }
}

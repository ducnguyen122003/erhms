package com.erhms.admin.dto;

import com.erhms.user.entity.User;

public record AdminAccountView(Long id, String email, String fullName, String phone,
                               String role, Long roleId, boolean accountEnabled, boolean accountLocked) {
    public static AdminAccountView from(User user) {
        return new AdminAccountView(user.id, user.email, user.fullName, user.phone,
                user.getRoleCode(), user.getRoleId(), user.accountEnabled, user.accountLocked);
    }
}

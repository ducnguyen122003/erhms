package com.erhms.role;

import java.util.Optional;

/** Fixed role IDs shared by authentication, dashboard and business authorization. */
public enum DashboardRole {
    ADMIN(1, "admin"), LECTURER(2, "lecturer"), STUDENT(3, "student"), ACCOUNTANT(4, "accountant"),
    IT_SUPPORT(5, "it-support"), MANAGER(6, "manager"), HR(7, "hr");

    private final long id;
    private final String slug;

    DashboardRole(long id, String slug) { this.id = id; this.slug = slug; }
    public long id() { return id; }
    public String slug() { return slug; }

    public static Optional<DashboardRole> fromId(Long roleId) {
        if (roleId == null) return Optional.empty();
        for (var role : values()) if (role.id == roleId) return Optional.of(role);
        return Optional.empty();
    }

    public static Optional<DashboardRole> resolve(Long roleId, String code) {
        return fromId(roleId).filter(role -> role.name().equals(code));
    }

    public static Optional<DashboardRole> bySlug(String slug) {
        for (var role : values()) if (role.slug.equals(slug)) return Optional.of(role);
        return Optional.empty();
    }
}

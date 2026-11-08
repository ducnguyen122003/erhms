package com.erhms.admin.dto;

import java.time.Instant;
import java.util.List;

/** Read model: deliberately excludes credentials, session identifiers and recovery data. */
public record AdminUserProfileView(AdminAccountView account, String roleName, Instant createdAt,
        Instant updatedAt, Instant lastLoginAt, List<Screen> functions, List<Activity> history) {
    public record Screen(String code, String screenCode, String name) {}
    public record Activity(String actionCode, String actionLabel, String actorName, String result,
                           String changeNote, Instant occurredAt) {}
}

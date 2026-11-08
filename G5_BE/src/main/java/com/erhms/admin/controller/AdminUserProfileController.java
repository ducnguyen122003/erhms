package com.erhms.admin.controller;

import com.erhms.admin.dto.AdminUserProfileView;
import com.erhms.admin.service.AdminUserProfileService;
import org.springframework.web.bind.annotation.*;

@RestController
public class AdminUserProfileController {
    private final AdminUserProfileService profiles;
    public AdminUserProfileController(AdminUserProfileService profiles) { this.profiles=profiles; }

    @GetMapping("/api/admin/users/{id}/profile")
    public AdminUserProfileView get(@PathVariable Long id) { return profiles.get(id); }
}

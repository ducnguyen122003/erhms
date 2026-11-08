package com.erhms.admin.controller;

import com.erhms.admin.service.ScreenPermissionsService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
public class ScreenPermissionsController {
    private final ScreenPermissionsService permissions;
    public ScreenPermissionsController(ScreenPermissionsService permissions) { this.permissions=permissions; }
    @GetMapping("/api/auth/permissions") public Map<String,Object> mine() { return permissions.mine(); }
    @GetMapping("/api/admin/permissions") public ScreenPermissionsService.Matrix matrix() { return permissions.matrix(); }
    @PutMapping("/api/admin/permissions") public ScreenPermissionsService.Matrix update(@Valid @RequestBody ScreenPermissionsService.Input input) { return permissions.update(input); }
}

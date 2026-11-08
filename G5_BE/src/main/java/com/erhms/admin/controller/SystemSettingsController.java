package com.erhms.admin.controller;

import com.erhms.admin.service.SystemSettingsService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
public class SystemSettingsController {
    private final SystemSettingsService settings;
    public SystemSettingsController(SystemSettingsService settings) { this.settings=settings; }
    @GetMapping("/api/system/branding") public Map<String,Object> branding() { return settings.branding(); }
    @GetMapping("/api/admin/settings") public Map<String,Object> get() { return settings.get(); }
    @PutMapping("/api/admin/settings") public Map<String,Object> update(@Valid @RequestBody SystemSettingsService.Input input) { return settings.update(input); }
    @PostMapping("/api/admin/settings/test-email") public Map<String,Object> testEmail() { return settings.testEmail(); }
}

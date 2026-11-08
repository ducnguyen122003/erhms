package com.erhms.admin.controller;

import com.erhms.admin.service.SystemAuditService;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/audit-logs")
public class SystemAuditController {
    private final SystemAuditService audit;
    public SystemAuditController(SystemAuditService audit) { this.audit=audit; }
    @GetMapping public Map<String,Object> list(@RequestParam(defaultValue="") String q,@RequestParam(defaultValue="") String action,
            @RequestParam(defaultValue="") String date,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="10") int size) { return audit.list(q,action,date,page,size); }
    @GetMapping("/{id}") public SystemAuditService.Entry detail(@PathVariable UUID id) { return audit.detail(id); }
}

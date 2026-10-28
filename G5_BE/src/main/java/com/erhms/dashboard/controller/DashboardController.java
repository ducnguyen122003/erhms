package com.erhms.dashboard.controller;

import com.erhms.dashboard.dto.DashboardView;
import com.erhms.dashboard.service.DashboardService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {
    private final DashboardService service;
    public DashboardController(DashboardService service) { this.service = service; }
    @GetMapping
    public DashboardView current() { return service.get(null); }
    @GetMapping("/{slug}")
    public DashboardView role(@PathVariable String slug) { return service.get(slug); }
}

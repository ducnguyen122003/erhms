package com.erhms.recruitment.controller;

import com.erhms.recruitment.dto.RequestDtos.Command;
import com.erhms.recruitment.dto.RequestDtos.EventView;
import com.erhms.recruitment.dto.RequestDtos.Input;
import com.erhms.recruitment.dto.RequestDtos.PageResult;
import com.erhms.recruitment.dto.RequestDtos.View;
import com.erhms.recruitment.enums.RequestStatus;
import com.erhms.recruitment.service.RecruitmentService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/recruitment-requests")
@PreAuthorize("hasAnyRole('USER', 'SERVICE_MANAGER')")
public class RecruitmentController {

    private final RecruitmentService service;

    public RecruitmentController(RecruitmentService service) {
        this.service = service;
    }

    @GetMapping
    public PageResult list(
            @RequestParam(defaultValue = "") String q,
            @RequestParam(required = false) RequestStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return service.list(q, status, page, size);
    }

    @GetMapping("/{id}")
    public View get(@PathVariable UUID id) {
        return service.get(id);
    }

    @GetMapping("/{id}/history")
    public List<EventView> history(@PathVariable UUID id) {
        return service.history(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('USER')")
    public View create(@Valid @RequestBody Input input) {
        return service.create(input);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('USER')")
    public View update(@PathVariable UUID id, @Valid @RequestBody Input input) {
        return service.update(id, input);
    }

    @PostMapping("/{id}/{action}")
    public View command(
            @PathVariable UUID id,
            @PathVariable String action,
            @Valid @RequestBody Command input) {
        return service.command(id, action, input);
    }
}

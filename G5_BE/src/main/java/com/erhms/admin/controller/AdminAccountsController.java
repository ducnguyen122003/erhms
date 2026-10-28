package com.erhms.admin.controller;

import com.erhms.admin.dto.AdminAccountInput;
import com.erhms.admin.dto.AdminAccountLockInput;
import com.erhms.admin.dto.AdminAccountPage;
import com.erhms.admin.dto.AdminAccountStatusInput;
import com.erhms.admin.dto.AdminAccountView;
import com.erhms.admin.dto.AdminRoleView;
import com.erhms.admin.service.AdminAccountsService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin")
public class AdminAccountsController {
    private final AdminAccountsService accounts;

    public AdminAccountsController(AdminAccountsService accounts) { this.accounts = accounts; }

    @GetMapping("/users")
    public AdminAccountPage list(@RequestParam(defaultValue = "") String q,
                                 @RequestParam(required = false) Long roleId,
                                 @RequestParam(defaultValue = "0") int page,
                                 @RequestParam(defaultValue = "10") int size) {
        return accounts.list(q, roleId, page, size);
    }

    @GetMapping("/roles")
    public List<AdminRoleView> roles() { return accounts.roles(); }

    @GetMapping("/users/{id}")
    public AdminAccountView detail(@PathVariable Long id) { return accounts.detail(id); }

    @PostMapping("/users")
    @ResponseStatus(HttpStatus.CREATED)
    public AdminAccountView create(@Valid @RequestBody AdminAccountInput input) { return accounts.create(input); }

    @PutMapping("/users/{id}")
    public AdminAccountView update(@PathVariable Long id, @Valid @RequestBody AdminAccountInput input) {
        return accounts.update(id, input);
    }

    @PatchMapping("/users/{id}/status")
    public AdminAccountView updateStatus(@PathVariable Long id, @Valid @RequestBody AdminAccountStatusInput input) {
        return accounts.updateStatus(id, input);
    }

    @PatchMapping("/users/{id}/lock")
    public AdminAccountView updateLock(@PathVariable Long id, @Valid @RequestBody AdminAccountLockInput input) {
        return accounts.updateLock(id, input);
    }

    @DeleteMapping("/users/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) { accounts.delete(id); }
}

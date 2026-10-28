package com.erhms.user.recovery;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/auth/password-reset")
public class PasswordResetController {
    private final PasswordResetService service;
    public PasswordResetController(PasswordResetService service) { this.service = service; }

    @PostMapping("/request")
    public Map<String, Object> request(@Valid @RequestBody PasswordResetInput.Request input, HttpServletRequest request) {
        return service.request(input, request.getRemoteAddr());
    }

    @PostMapping("/verify")
    public Map<String, Object> verify(@Valid @RequestBody PasswordResetInput.Verify input) { return service.verify(input); }

    @PostMapping("/complete")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void complete(@Valid @RequestBody PasswordResetInput.Complete input) { service.complete(input); }
}

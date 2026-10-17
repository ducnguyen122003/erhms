package com.erhms.user.controller;

import com.erhms.user.dto.PasswordChangeInput;
import com.erhms.user.dto.ProfileInput;
import com.erhms.user.dto.UserView;
import com.erhms.user.service.PasswordChangeService;
import com.erhms.user.service.UserProfileService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final UserProfileService profiles;
    private final PasswordChangeService passwords;

    public AuthController(UserProfileService profiles, PasswordChangeService passwords) {
        this.profiles = profiles;
        this.passwords = passwords;
    }

    @GetMapping("/csrf")
    public Map<String, String> csrf(CsrfToken token) {
        return Map.of("token", token.getToken(), "headerName", token.getHeaderName());
    }

    @GetMapping("/me")
    public UserView me() {
        return profiles.get();
    }

    @PutMapping("/me")
    public UserView updateMe(@Valid @RequestBody ProfileInput input) {
        return profiles.update(input);
    }

    @PostMapping("/change-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(@RequestBody PasswordChangeInput input) {
        passwords.change(input);
    }
}

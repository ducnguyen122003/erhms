package com.erhms.user.controller;

import com.erhms.user.dto.UserView;
import com.erhms.user.service.CurrentUser;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final CurrentUser current;

    public AuthController(CurrentUser current) {
        this.current = current;
    }

    @GetMapping("/csrf")
    public Map<String, String> csrf(CsrfToken token) {
        return Map.of("token", token.getToken(), "headerName", token.getHeaderName());
    }

    @GetMapping("/me")
    public UserView me() {
        var user = current.get();
        return new UserView(user.id, user.email, user.fullName, user.getRoleCode(), user.getRoleId());
    }
}

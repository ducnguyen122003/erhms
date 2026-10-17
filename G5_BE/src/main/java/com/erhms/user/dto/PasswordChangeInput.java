package com.erhms.user.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PasswordChangeInput(
        String oldPassword, String newPassword, String confirmPassword) {
    @Override
    public String toString() {
        return "PasswordChangeInput[REDACTED]";
    }
}

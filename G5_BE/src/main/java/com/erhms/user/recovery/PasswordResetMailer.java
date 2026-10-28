package com.erhms.user.recovery;

public interface PasswordResetMailer {
    void requireConfigured();
    void sendCode(String email, String code);
}

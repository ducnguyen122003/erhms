package com.erhms.user.onboarding;

public interface StudentAccountMailer {
    void requireConfigured();
    void sendCredentials(String email, String fullName, String password);
}

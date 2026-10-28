package com.erhms.user.recovery;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Locale;

public final class PasswordResetInput {
    private PasswordResetInput() {}

    static String normalize(String email) {
        return email == null ? null : email.strip().toLowerCase(Locale.ROOT);
    }

    public record Request(
            @NotBlank(message = "Nhập email đã đăng ký")
            @Email(message = "Email không hợp lệ")
            @Size(max = 190, message = "Email tối đa 190 ký tự") String email) {
        public Request { email = normalize(email); }
    }

    public record Verify(
            @NotBlank @Email @Size(max = 190) String email,
            @NotBlank(message = "Nhập mã xác nhận")
            @Pattern(regexp = "[0-9]{6}", message = "Mã xác nhận phải gồm 6 chữ số") String code) {
        public Verify { email = normalize(email); }
        @Override public String toString() { return "PasswordResetVerify[REDACTED]"; }
    }

    public record Complete(
            @NotBlank @Email @Size(max = 190) String email,
            @NotBlank @Size(max = 128) String resetToken,
            String newPassword, String confirmPassword) {
        public Complete { email = normalize(email); }
        @Override public String toString() { return "PasswordResetComplete[REDACTED]"; }
    }
}

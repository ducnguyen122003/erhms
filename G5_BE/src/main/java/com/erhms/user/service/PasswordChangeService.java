package com.erhms.user.service;

import com.erhms.shared.exception.BusinessException;
import com.erhms.user.dto.PasswordChangeInput;
import com.erhms.user.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;

@Service
public class PasswordChangeService {
    private final CurrentUser current;
    private final UserRepository users;
    private final PasswordEncoder encoder;

    public PasswordChangeService(CurrentUser current, UserRepository users, PasswordEncoder encoder) {
        this.current = current;
        this.users = users;
        this.encoder = encoder;
    }

    @Transactional
    public void change(PasswordChangeInput input) {
        requireValue(input.oldPassword(), "Nhập mật khẩu hiện tại");
        requireValue(input.newPassword(), "Nhập mật khẩu mới");
        requireValue(input.confirmPassword(), "Nhập lại mật khẩu mới");
        requireBcryptLength(input.oldPassword(), "Mật khẩu hiện tại");
        requireBcryptLength(input.newPassword(), "Mật khẩu mới");
        requireBcryptLength(input.confirmPassword(), "Mật khẩu xác nhận");
        if (input.newPassword().codePointCount(0, input.newPassword().length()) < 8) {
            throw invalid("Mật khẩu mới phải có ít nhất 8 ký tự");
        }
        if (!input.newPassword().equals(input.confirmPassword())) {
            throw invalid("Mật khẩu xác nhận không khớp với mật khẩu mới");
        }

        // Lock before loading the hash: only one request can verify/change this account at a time.
        var user = current.getForUpdate();
        if (!encoder.matches(input.oldPassword(), user.passwordHash)) {
            throw invalid("Mật khẩu hiện tại không đúng");
        }
        user.passwordHash = encoder.encode(input.newPassword());
        users.saveAndFlush(user);
    }

    private static void requireValue(String password, String message) {
        if (password == null || password.isBlank()) {
            throw invalid(message);
        }
    }

    private static void requireBcryptLength(String password, String label) {
        if (password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw invalid(label + " không được vượt quá 72 byte UTF-8");
        }
    }

    private static BusinessException invalid(String message) {
        return new BusinessException(HttpStatus.BAD_REQUEST, message);
    }
}

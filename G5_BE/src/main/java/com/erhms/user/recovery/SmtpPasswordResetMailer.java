package com.erhms.user.recovery;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

import java.util.ArrayList;

@Component
public class SmtpPasswordResetMailer implements PasswordResetMailer {
    private static final Logger LOG = LoggerFactory.getLogger(SmtpPasswordResetMailer.class);
    private final JavaMailSender sender;
    private final String host, username, password, from;
    private final boolean authentication;

    public SmtpPasswordResetMailer(JavaMailSender sender,
            @Value("${spring.mail.host:}") String host,
            @Value("${spring.mail.username:}") String username,
            @Value("${spring.mail.password:}") String password,
            @Value("${app.mail.from:}") String from,
            @Value("${spring.mail.properties.mail.smtp.auth:true}") boolean authentication) {
        this.sender = sender;
        this.host = host == null ? "" : host;
        this.username = username == null ? "" : username;
        this.password = password == null ? "" : password;
        // A defined but empty SMTP_FROM must not hide a configured sender account.
        this.from = from == null || from.isBlank() ? this.username.strip() : from.strip();
        this.authentication = authentication;
    }

    @Override public void requireConfigured() {
        var missing = new ArrayList<String>();
        if (host.isBlank()) missing.add("spring.mail.host");
        if (from.isBlank()) missing.add("app.mail.from");
        if (authentication && username.isBlank()) missing.add("spring.mail.username");
        if (authentication && password.isBlank()) missing.add("spring.mail.password");
        if (!missing.isEmpty()) {
            // Log only property names: credentials, recipients and codes stay private.
            LOG.warn("Thiếu cấu hình gửi email: {}", String.join(", ", missing));
            throw new PasswordResetException(HttpStatus.SERVICE_UNAVAILABLE, "MAIL_NOT_CONFIGURED",
                    "Chưa cấu hình máy chủ gửi email. Vui lòng liên hệ quản trị viên.", 0);
        }
    }

    @Override public void sendCode(String email, String code) {
        requireConfigured();
        var message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(email);
        message.setSubject("ERHMS - Mã xác nhận đặt lại mật khẩu");
        message.setText("Mã xác nhận của bạn là: " + code
                + "\n\nMã gồm 6 chữ số, có hiệu lực trong 10 phút và chỉ sử dụng một lần."
                + "\nKhông chia sẻ mã này với người khác. Nếu bạn không yêu cầu đặt lại mật khẩu, hãy bỏ qua email."
                + "\n\nERHMS - Trung tâm đào tạo");
        sender.send(message);
    }
}

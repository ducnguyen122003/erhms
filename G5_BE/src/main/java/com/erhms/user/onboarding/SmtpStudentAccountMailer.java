package com.erhms.user.onboarding;

import com.erhms.admin.service.SystemSettingsService;
import com.erhms.workspace.WorkflowException;
import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.Map;
import java.util.Objects;

@Component
public class SmtpStudentAccountMailer implements StudentAccountMailer {
    private final JavaMailSender sender;
    private final String host, username, smtpPassword, from, loginUrl;
    private final boolean authentication;
    private ObjectProvider<SystemSettingsService> settings;

    public SmtpStudentAccountMailer(JavaMailSender sender,
            @Value("${spring.mail.host:}") String host,
            @Value("${spring.mail.username:}") String username,
            @Value("${spring.mail.password:}") String smtpPassword,
            @Value("${app.mail.from:}") String from,
            @Value("${spring.mail.properties.mail.smtp.auth:true}") boolean authentication,
            @Value("${app.student-accounts.login-url:http://localhost:8082/login}") String loginUrl) {
        this.sender = sender;
        this.host = clean(host);
        this.username = clean(username);
        this.smtpPassword = Objects.requireNonNullElse(smtpPassword, "");
        this.from = clean(from).isEmpty() ? this.username : clean(from);
        this.authentication = authentication;
        this.loginUrl = clean(loginUrl);
    }

    @Autowired
    public void setSystemSettings(ObjectProvider<SystemSettingsService> settings) {
        this.settings = settings;
    }

    @Override
    public void requireConfigured() {
        if (host.isBlank() || !singleAddress(from)
                || (authentication && (username.isBlank() || smtpPassword.isBlank()))
                || !validLoginUrl(loginUrl)) {
            throw new WorkflowException(HttpStatus.SERVICE_UNAVAILABLE, "MAIL_NOT_CONFIGURED",
                    "Chưa cấu hình đầy đủ email cấp tài khoản học viên. Vui lòng liên hệ quản trị viên.", Map.of());
        }
    }

    @Override
    public void sendCredentials(String email, String fullName, String password) {
        requireConfigured();
        if (!singleAddress(email)) throw deliveryFailure();
        var configured = settings == null ? null : settings.getIfAvailable();
        var branding = configured == null ? Map.<String, Object>of() : configured.branding();
        String centerName = clean(Objects.toString(branding.get("centerName"), "BK-VTCMS"))
                .replace('\r', ' ').replace('\n', ' ');
        if (centerName.isBlank()) centerName = "BK-VTCMS";
        String replyTo = clean(Objects.toString(branding.get("notificationEmail"), ""));
        var message = new CredentialsMessage();
        message.setFrom(from);
        message.setTo(email);
        if (singleAddress(replyTo)) message.setReplyTo(replyTo);
        message.setSubject(centerName + " - Tài khoản học viên của bạn");
        message.setText("Xin chào " + Objects.requireNonNullElse(fullName, "học viên") + ","
                + "\n\n" + centerName + " đã tạo tài khoản học viên cho bạn."
                + "\n\nTrang đăng nhập: " + loginUrl
                + "\nEmail đăng nhập: " + email
                + "\nMật khẩu ban đầu: " + password
                + "\n\nVui lòng đổi mật khẩu ngay sau lần đăng nhập đầu tiên tại Thông tin cá nhân → Đổi mật khẩu."
                + "\nKhông chia sẻ mật khẩu hoặc chuyển tiếp email này cho người khác."
                + "\n\n" + centerName);
        try {
            // Delivery is synchronous so a failure can roll back the account creation transaction.
            sender.send(message);
        } catch (MailException ignored) {
            // SMTP exceptions may contain the message body; never retain or log the original cause.
            throw deliveryFailure();
        }
    }

    private static String clean(String value) {
        return Objects.requireNonNullElse(value, "").strip();
    }

    private static boolean singleAddress(String value) {
        if (value == null || value.isBlank() || value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0) return false;
        try {
            var addresses = InternetAddress.parse(value, true);
            if (addresses.length != 1 || addresses[0].isGroup()) return false;
            addresses[0].validate();
            return true;
        } catch (AddressException ignored) {
            return false;
        }
    }

    private static boolean validLoginUrl(String value) {
        try {
            URI uri = URI.create(value);
            return ("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                    && uri.getHost() != null && uri.getRawUserInfo() == null;
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    private static WorkflowException deliveryFailure() {
        return new WorkflowException(HttpStatus.BAD_GATEWAY, "MAIL_DELIVERY_FAILED",
                "Không gửi được email cấp tài khoản học viên. Vui lòng thử lại hoặc liên hệ quản trị viên.", Map.of());
    }

    private static final class CredentialsMessage extends SimpleMailMessage {
        @Override public String toString() { return "StudentAccountEmail[REDACTED]"; }
    }
}

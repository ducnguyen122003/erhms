package com.erhms.admin.service;

import com.erhms.shared.exception.BusinessException;
import com.erhms.user.service.CurrentUser;
import jakarta.validation.constraints.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;

@Service
public class SystemSettingsService {
    private final JdbcTemplate jdbc;
    private final ScreenPermissionsService permissions;
    private final CurrentUser current;
    private final JavaMailSender sender;
    private final String host,username,password;
    public SystemSettingsService(JdbcTemplate jdbc,ScreenPermissionsService permissions,CurrentUser current,JavaMailSender sender,
            @Value("${spring.mail.host:}") String host,@Value("${spring.mail.username:}") String username,@Value("${spring.mail.password:}") String password) {
        this.jdbc=jdbc;this.permissions=permissions;this.current=current;this.sender=sender;this.host=host;this.username=username;this.password=password;
    }
    public record Input(@NotBlank @Size(max=160) String centerName,@NotBlank @Email @Size(max=190) String notificationEmail,
                        @NotNull @Pattern(regexp="vi") String defaultLanguage,@NotNull Long version) {}
    public Map<String,Object> branding() {
        return jdbc.queryForMap("SELECT center_name AS \"centerName\",notification_email AS \"notificationEmail\",default_language AS \"defaultLanguage\" FROM system_settings WHERE id=1");
    }
    @Transactional(readOnly=true)
    public Map<String,Object> get() {
        permissions.require("SYSTEM_CONFIG");
        var result=new LinkedHashMap<String,Object>(branding());
        var row=jdbc.queryForMap("SELECT version,updated_at,u.full_name FROM system_settings s LEFT JOIN m_user u ON u.user_id=s.updated_by WHERE s.id=1");
        result.put("version",row.get("version"));
        result.put("updatedAt",row.get("updated_at"));result.put("updatedBy",row.get("full_name"));
        result.put("timezone","Asia/Ho_Chi_Minh");
        result.put("mailStatus",mailConfigured()?"CONFIGURED":"NOT_CONFIGURED");
        result.put("bankingStatus","NOT_CONFIGURED");
        result.put("paymentReference","HD-{mã hợp đồng}");
        result.put("passwordResetPolicy","Mã xác nhận: 10 phút · Phiên đổi mật khẩu: 5 phút");
        return result;
    }
    @Transactional
    public Map<String,Object> update(Input input) {
        long version=jdbc.queryForObject("SELECT version FROM system_settings WHERE id=1 FOR UPDATE",Long.class);
        permissions.require("SYSTEM_CONFIG");
        if (version!=input.version()) throw new BusinessException(HttpStatus.CONFLICT,"Cấu hình vừa thay đổi. Hãy tải lại trước khi lưu.");
        jdbc.update("UPDATE system_settings SET center_name=?,notification_email=?,default_language=?,version=version+1,updated_at=?,updated_by=? WHERE id=1",
                input.centerName().strip(),input.notificationEmail().strip(),input.defaultLanguage(),Timestamp.from(Instant.now()),current.get().id);
        return get();
    }
    public Map<String,Object> testEmail() {
        permissions.require("SYSTEM_CONFIG");
        if (!mailConfigured()) throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE,"Chưa cấu hình đầy đủ Gmail SMTP trong môi trường backend.");
        try {
            if (!(sender instanceof JavaMailSenderImpl smtp)) throw new IllegalStateException();
            smtp.testConnection();
            return Map.of("connected",true,"message","Kết nối và xác thực SMTP thành công. Chưa gửi email thử.");
        } catch(Exception ignored) {
            throw new BusinessException(HttpStatus.BAD_GATEWAY,"Không kết nối hoặc xác thực được SMTP. Kiểm tra mật khẩu ứng dụng, STARTTLS và mạng của máy chủ.");
        }
    }
    private boolean mailConfigured() { return !host.isBlank()&&!username.isBlank()&&!password.isBlank(); }
}

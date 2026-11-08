package com.erhms.admin.security;

import com.erhms.admin.service.SystemAuditService;
import com.erhms.admin.service.SystemAuditService.Actor;
import com.erhms.user.repository.UserRepository;
import com.erhms.user.security.UserPrincipal;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.List;

/** Stores metadata only. Request bodies, passwords, reset codes and tokens are never inspected. */
public class OperationAuditFilter extends OncePerRequestFilter {
    private final SystemAuditService audit;
    private final UserRepository users;
    public OperationAuditFilter(SystemAuditService audit,UserRepository users) { this.audit=audit; this.users=users; }
    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain) throws ServletException,IOException {
        String path=request.getRequestURI(),method=request.getMethod();
        if (!path.startsWith("/api/") || !List.of("POST","PUT","PATCH","DELETE").contains(method)) { chain.doFilter(request,response); return; }
        Actor before=actor(); boolean failed=false;
        try { chain.doFilter(request,response); }
        catch(ServletException|IOException|RuntimeException e) { failed=true; throw e; }
        finally {
            try {
                Actor after=actor();
                String action=request.getAttribute("workflow.action") instanceof String suppliedAction?suppliedAction:action(path,method),note=(String)request.getAttribute("admin.changeNote");
                if (note!=null && note.length()>1000) note=note.substring(0,1000);
                String targetId=request.getAttribute("admin.targetId") instanceof String supplied?supplied:target(path);
                String operationLabel=request.getAttribute("workflow.label") instanceof String suppliedLabel?suppliedLabel:label(action);
                audit.record(after!=null?after:before,action,operationLabel,targetId,method,safePath(path),failed?500:response.getStatus(),note);
            } catch(RuntimeException ignored) {
                LoggerFactory.getLogger(OperationAuditFilter.class).warn("System operation audit could not be persisted; no request data was logged.");
            }
        }
    }
    private Actor actor() {
        Authentication auth=SecurityContextHolder.getContext().getAuthentication();
        if (auth==null || !auth.isAuthenticated()) return null;
        var found=auth.getPrincipal() instanceof UserPrincipal principal?users.findWithRoleById(principal.getUserId()):users.findByEmail(auth.getName());
        return found.map(u->new Actor(u.id,u.fullName,u.getRoleCode())).orElse(null);
    }
    private static String safePath(String path) { return path.length()>250?path.substring(0,250):path; }
    private static String target(String path) {
        String[] parts=path.split("/");
        if (parts.length>4 && parts[3].equals("users")) return parts[4].length()<=100?parts[4]:null;
        if (parts.length>3 && parts[2].equals("recruitment-requests")) return parts[3];
        return null;
    }
    private static String action(String path,String method) {
        if (path.equals("/api/auth/login")) return "LOGIN";
        if (path.equals("/api/auth/logout")) return "LOGOUT";
        if (path.contains("password-reset")) return "PASSWORD_RECOVERY";
        if (path.endsWith("change-password")) return "PASSWORD_CHANGE";
        if (path.equals("/api/auth/me")) return "PROFILE_UPDATE";
        if (path.startsWith("/api/admin/permissions")) return "PERMISSIONS_UPDATE";
        if (path.endsWith("/test-email")) return "SMTP_CHECK";
        if (path.startsWith("/api/admin/settings")) return "SETTINGS_UPDATE";
        if (path.startsWith("/api/admin/users")) {
            if (method.equals("DELETE")) return "ACCOUNT_DELETE";
            if (path.endsWith("/lock")) return "ACCOUNT_LOCK_CHANGE";
            if (path.endsWith("/status")) return "ACCOUNT_STATUS_CHANGE";
            return method.equals("POST")?"ACCOUNT_CREATE":"ACCOUNT_UPDATE";
        }
        if (path.startsWith("/api/recruitment-requests")) return "RECRUITMENT_"+method;
        if (path.equals("/api/public/consultations")) return "CONSULTATION_CREATE";
        return "SYSTEM_"+method;
    }
    private static String label(String action) {
        return switch(action) {
            case "LOGIN" -> "Đăng nhập hệ thống"; case "LOGOUT" -> "Đăng xuất hệ thống";
            case "ACCOUNT_CREATE" -> "Tạo tài khoản"; case "ACCOUNT_UPDATE" -> "Cập nhật tài khoản";
            case "ACCOUNT_DELETE" -> "Xóa tài khoản"; case "ACCOUNT_LOCK_CHANGE" -> "Khóa / mở khóa tài khoản";
            case "ACCOUNT_STATUS_CHANGE" -> "Kích hoạt / vô hiệu hóa tài khoản"; case "PERMISSIONS_UPDATE" -> "Cập nhật phân quyền";
            case "SETTINGS_UPDATE" -> "Cập nhật cấu hình hệ thống"; case "SMTP_CHECK" -> "Kiểm tra kết nối email";
            case "PROFILE_UPDATE" -> "Cập nhật thông tin cá nhân"; case "PASSWORD_CHANGE" -> "Đổi mật khẩu";
            case "PASSWORD_RECOVERY" -> "Khôi phục mật khẩu"; case "CONSULTATION_CREATE" -> "Đăng ký tư vấn";
            default -> action.startsWith("RECRUITMENT_")?"Thao tác yêu cầu tuyển dụng":"Thao tác hệ thống";
        };
    }
}

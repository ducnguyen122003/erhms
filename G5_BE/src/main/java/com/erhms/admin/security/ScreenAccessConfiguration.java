package com.erhms.admin.security;

import com.erhms.admin.service.ScreenPermissionsService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class ScreenAccessConfiguration implements WebMvcConfigurer {
    private final ScreenPermissionsService permissions;
    public ScreenAccessConfiguration(ScreenPermissionsService permissions) { this.permissions=permissions; }
    @Override public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new HandlerInterceptor() {
            @Override public boolean preHandle(HttpServletRequest request,HttpServletResponse response,Object handler) {
                String path=request.getRequestURI(),code=null;
                if (path.startsWith("/api/admin/users") || path.equals("/api/admin/roles")) code="USER_ACCOUNTS";
                else if (path.startsWith("/api/admin/permissions")) code="ROLE_PERMISSIONS";
                else if (path.startsWith("/api/admin/settings")) code="SYSTEM_CONFIG";
                else if (path.startsWith("/api/admin/audit-logs")) code="SYSTEM_AUDIT_LOG";
                else if (path.startsWith("/api/dashboard")) code="DASHBOARD";
                else if (path.startsWith("/api/recruitment-requests")) code="RECRUITMENT";
                else if (path.equals("/api/auth/change-password") || (path.equals("/api/auth/me") && !request.getMethod().equals("GET"))) code="PROFILE";
                if (code!=null) permissions.require(code);
                return true;
            }
        }).addPathPatterns("/api/**");
    }
}

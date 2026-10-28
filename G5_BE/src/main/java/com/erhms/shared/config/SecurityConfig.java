package com.erhms.shared.config;

import com.erhms.dashboard.repository.DashboardRepository;
import com.erhms.role.DashboardRole;
import com.erhms.role.InvalidRoleException;
import com.erhms.user.repository.UserRepository;
import com.erhms.user.security.UserPrincipal;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {
    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    UserDetailsService userDetailsService(UserRepository users) {
        return email -> {
            var matches = users.findAllByEmailIgnoreCase(email.strip());
            if (matches.size() != 1) throw new UsernameNotFoundException("Không tìm thấy tài khoản");
            var user = matches.get(0);
            return new UserPrincipal(user.id, user.email, user.passwordHash, user.getRoleId(), user.getRoleCode(),
                    user.accountEnabled, user.accountLocked, user.credentialsVersion);
        };
    }

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http, DashboardRepository dashboards, UserRepository users) throws Exception {
        http.authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/api/auth/csrf", "/actuator/health", "/error").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/public/consultations").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/password-reset/request", "/api/auth/password-reset/verify",
                                "/api/auth/password-reset/complete").permitAll()
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .anyRequest().authenticated())
                .requestCache(cache -> cache.disable())
                .formLogin(form -> form.loginProcessingUrl("/api/auth/login")
                        .successHandler((request, response, authentication) -> {
                            var principal = authentication.getPrincipal() instanceof UserPrincipal user ? user : null;
                            var account = principal == null ? null : users.findWithRoleById(principal.getUserId()).orElse(null);
                            if (principal == null || !principal.hasSupportedRole() || account == null
                                    || DashboardRole.resolve(account.getRoleId(), account.getRoleCode()).isEmpty()) {
                                SecurityContextHolder.clearContext();
                                var session = request.getSession(false);
                                if (session != null) session.invalidate();
                                response.setStatus(403);
                                response.setContentType("application/json;charset=UTF-8");
                                response.getWriter().write("{\"code\":\"INVALID_ROLE\",\"message\":\"" + InvalidRoleException.MESSAGE + "\"}");
                                return;
                            }
                            // Recheck persisted flags in case an administrator changed them during authentication.
                            if (account.accountLocked || !account.accountEnabled) {
                                SecurityContextHolder.clearContext();
                                var session = request.getSession(false);
                                if (session != null) session.invalidate();
                                writeAccountStatusError(response, account.accountLocked);
                                return;
                            }
                            if (principal.getCredentialsVersion() != account.credentialsVersion) {
                                SecurityContextHolder.clearContext();
                                var session = request.getSession(false);
                                if (session != null) session.invalidate();
                                response.setStatus(401);
                                response.setContentType("application/json;charset=UTF-8");
                                response.getWriter().write("{\"code\":\"SESSION_EXPIRED\",\"message\":\"Thông tin tài khoản đã thay đổi. Vui lòng đăng nhập lại.\"}");
                                return;
                            }
                            dashboards.recordLogin(principal.getUserId());
                            response.setStatus(204);
                        })
                        .failureHandler((request, response, exception) -> {
                            if (exception instanceof LockedException || exception instanceof DisabledException) {
                                writeAccountStatusError(response, exception instanceof LockedException);
                                return;
                            }
                            response.setStatus(401);
                            response.setContentType("application/json;charset=UTF-8");
                            response.getWriter().write("{\"message\":\"Email hoặc mật khẩu không đúng\"}");
                        })
                        .permitAll())
                .logout(logout -> logout.logoutUrl("/api/auth/logout")
                        .invalidateHttpSession(true)
                        .deleteCookies("JSESSIONID")
                        .logoutSuccessHandler((request, response, authentication) -> response.setStatus(204)))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, exception) -> {
                            response.setStatus(401);
                            response.setContentType("application/json;charset=UTF-8");
                            response.getWriter().write("{\"message\":\"Vui lòng đăng nhập lại\"}");
                        })
                        .accessDeniedHandler((request, response, exception) -> {
                            response.setStatus(403);
                            response.setContentType("application/json;charset=UTF-8");
                            response.getWriter().write("{\"message\":\"Không có quyền hoặc phiên bảo mật đã hết hạn. Hãy tải lại trang.\"}");
                        }));
        return http.build();
    }

    private static void writeAccountStatusError(HttpServletResponse response, boolean locked) throws IOException {
        response.setStatus(401);
        response.setContentType("application/json;charset=UTF-8");
        String code = locked ? "ACCOUNT_LOCKED" : "ACCOUNT_DISABLED";
        String message = locked
                ? "Tài khoản đang bị khóa. Vui lòng liên hệ quản trị viên để khôi phục."
                : "Tài khoản đang bị vô hiệu hóa. Vui lòng liên hệ quản trị viên để khôi phục.";
        response.getWriter().write("{\"code\":\"" + code + "\",\"message\":\"" + message + "\"}");
    }
}

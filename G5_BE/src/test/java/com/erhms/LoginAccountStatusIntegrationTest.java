package com.erhms;

import com.erhms.role.entity.Role;
import com.erhms.role.repository.RoleRepository;
import com.erhms.user.entity.User;
import com.erhms.user.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.reset;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:erhms_login_status_test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class LoginAccountStatusIntegrationTest {
    private static final String PASSWORD = "AccountStatus@2026";
    private static final String LOCKED_MESSAGE =
            "Tài khoản đang bị khóa. Vui lòng liên hệ quản trị viên để khôi phục.";
    private static final String DISABLED_MESSAGE =
            "Tài khoản đang bị vô hiệu hóa. Vui lòng liên hệ quản trị viên để khôi phục.";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserRepository users;
    @Autowired RoleRepository roles;
    @Autowired JdbcTemplate jdbc;
    @SpyBean PasswordEncoder encoder;

    private User account;

    @BeforeEach
    void createAccount() {
        account = new User();
        account.email = UUID.randomUUID() + "@login-status.test";
        account.fullName = "Người kiểm thử đăng nhập";
        account.passwordHash = encoder.encode(PASSWORD);
        account.role = roles.findByCode(Role.STUDENT).orElseThrow();
        account = users.saveAndFlush(account);
    }

    @AfterEach
    void restorePasswordEncoder() {
        reset(encoder);
    }

    @ParameterizedTest
    @CsvSource({"true,true", "false,false", "false,true"})
    void blockedAccountReturnsSpecificUtf8MessageWithoutAuthenticationOrLoginEvent(boolean enabled, boolean locked)
            throws Exception {
        setFlags(enabled, locked);
        var result = login(account.email, PASSWORD).andExpect(status().isUnauthorized()).andReturn();
        assertStatusResponse(result, locked);
        assertAnonymousWithoutLoginEvent(result);
    }

    @ParameterizedTest
    @CsvSource({"true,true", "false,false", "false,true"})
    void restoringBothFlagsAllowsFreshLogin(boolean enabled, boolean locked) throws Exception {
        setFlags(enabled, locked);
        login(account.email, PASSWORD).andExpect(status().isUnauthorized());
        setFlags(true, false);

        var result = login(account.email, PASSWORD).andExpect(status().isNoContent()).andReturn();
        var session = (MockHttpSession) result.getRequest().getSession(false);
        assertNotNull(session);
        mvc.perform(get("/api/auth/me").session(session)).andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(account.id));
        mvc.perform(get("/api/dashboard").session(session)).andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value(Role.STUDENT));
        assertEquals(1L, loginEventCount());
    }

    @ParameterizedTest
    @CsvSource({"true,true", "false,false", "false,true"})
    void persistedStatusChangeDuringPasswordVerificationRejectsLoginAndClearsNewSession(boolean enabled, boolean locked)
            throws Exception {
        // The provider already loaded enabled/unlocked flags before verifying this password.
        // Simulate an administrator changing the database while that verification is in progress.
        doAnswer(invocation -> {
            boolean matches = (boolean) invocation.callRealMethod();
            setFlags(enabled, locked);
            return matches;
        }).when(encoder).matches(eq(PASSWORD), eq(account.passwordHash));

        var result = login(account.email, PASSWORD).andExpect(status().isUnauthorized()).andReturn();
        assertStatusResponse(result, locked);
        assertAnonymousWithoutLoginEvent(result);
    }

    @Test
    void activeAccountWithWrongPasswordStillReturnsGenericFailure() throws Exception {
        assertGenericFailure(login(account.email, "IncorrectPassword@2026").andExpect(status().isUnauthorized()).andReturn());
    }

    @Test
    void unknownEmailStillReturnsGenericFailure() throws Exception {
        assertGenericFailure(login(UUID.randomUUID() + "@missing.test", PASSWORD)
                .andExpect(status().isUnauthorized()).andReturn());
    }

    @Test
    void loginStillRequiresCsrfEvenForBlockedAccount() throws Exception {
        setFlags(false, true);
        var result = mvc.perform(post("/api/auth/login").param("username", account.email).param("password", PASSWORD))
                .andExpect(status().isForbidden()).andReturn();
        assertAnonymousWithoutLoginEvent(result);
    }

    private ResultActions login(String email, String password) throws Exception {
        return mvc.perform(post("/api/auth/login").with(csrf()).param("username", email).param("password", password));
    }

    private void setFlags(boolean enabled, boolean locked) {
        jdbc.update("UPDATE m_user SET account_enabled=?,account_locked=?,credentials_version=credentials_version+1 WHERE user_id=?",
                enabled, locked, account.id);
    }

    private void assertStatusResponse(MvcResult result, boolean locked) throws Exception {
        assertEquals("UTF-8", result.getResponse().getCharacterEncoding());
        var body = json.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
        assertEquals(locked ? "ACCOUNT_LOCKED" : "ACCOUNT_DISABLED", body.path("code").asText());
        assertEquals(locked ? LOCKED_MESSAGE : DISABLED_MESSAGE, body.path("message").asText());
        assertFalse(result.getResponse().getContentAsString(StandardCharsets.UTF_8).contains(PASSWORD));
    }

    private void assertGenericFailure(MvcResult result) throws Exception {
        var body = json.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
        assertEquals("Email hoặc mật khẩu không đúng", body.path("message").asText());
        assertFalse(body.has("code"));
        assertAnonymousWithoutLoginEvent(result);
    }

    private void assertAnonymousWithoutLoginEvent(MvcResult result) throws Exception {
        var session = (MockHttpSession) result.getRequest().getSession(false);
        if (session != null) {
            var context = (SecurityContext) session.getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
            assertTrue(context == null || context.getAuthentication() == null || !context.getAuthentication().isAuthenticated());
            mvc.perform(get("/api/auth/me").session(session)).andExpect(status().isUnauthorized());
            mvc.perform(get("/api/dashboard").session(session)).andExpect(status().isUnauthorized());
        } else {
            mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
            mvc.perform(get("/api/dashboard")).andExpect(status().isUnauthorized());
        }
        assertEquals(0L, loginEventCount(), "Failed login must not create a successful login event");
    }

    private long loginEventCount() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM user_login_events WHERE user_id=?", Long.class, account.id);
    }
}

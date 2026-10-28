package com.erhms;

import com.erhms.role.entity.Role;
import com.erhms.role.repository.RoleRepository;
import com.erhms.user.entity.User;
import com.erhms.user.recovery.PasswordResetException;
import com.erhms.user.recovery.PasswordResetMailer;
import com.erhms.user.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:erhms_password_reset_test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PasswordResetIntegrationTest {
    private static final String API = "/api/auth/password-reset";
    private static final String PASSWORD = "ExistingPassword@2026";
    private static final String NEW_PASSWORD = "ChangedPassword@2026";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired UserRepository users;
    @Autowired RoleRepository roles;
    @Autowired PasswordEncoder encoder;
    @MockitoBean PasswordResetMailer mailer;

    private User owner;
    private String unique;
    private final Map<String, Integer> deliveryCounts = new HashMap<>();

    @BeforeEach
    void setup() {
        jdbc.update("delete from password_reset_challenges");
        jdbc.update("delete from password_reset_rate_limits");
        reset(mailer);
        deliveryCounts.clear();
        unique = UUID.randomUUID().toString();
        owner = seed("owner-" + unique + "@recovery.test", Role.STUDENT);
    }

    @Test
    void requestDoesNotRevealWhetherAnEmailExistsAndNeverReturnsCodesOrCredentials() throws Exception {
        var unknown = request("unknown-" + unique + "@recovery.test").andExpect(status().isOk())
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.expiresInSeconds").value(600))
                .andExpect(jsonPath("$.retryAfterSeconds").value(60))
                .andExpect(jsonPath("$.code").doesNotHaveJsonPath())
                .andExpect(jsonPath("$.resetToken").doesNotHaveJsonPath())
                .andExpect(jsonPath("$.passwordHash").doesNotHaveJsonPath()).andReturn();
        verify(mailer, never()).sendCode(anyString(), anyString());
        var known = request("  " + owner.email.toUpperCase(Locale.ROOT) + "  ").andExpect(status().isOk()).andReturn();
        assertEquals(body(unknown.getResponse().getContentAsString(StandardCharsets.UTF_8)),
                body(known.getResponse().getContentAsString(StandardCharsets.UTF_8)));
        String code = deliveredCode(owner.email);
        assertTrue(code.matches("[0-9]{6}"));
        assertFalse(known.getResponse().getContentAsString(StandardCharsets.UTF_8).contains(code));
        var context = known.getRequest().getSession(false);
        assertTrue(context == null || context.getAttribute("SPRING_SECURITY_CONTEXT") == null);
        String stored = jdbc.queryForObject("select code_hash from password_reset_challenges where email_key = ?",
                String.class, key(owner.email));
        assertNotEquals(code, stored);
        assertTrue(stored.startsWith("$2"));
        assertEquals(owner.passwordHash, users.findById(owner.id).orElseThrow().passwordHash);
    }

    @Test
    void disabledLockedAndUnsupportedAccountsReceiveTheSameResponseWithoutMail() throws Exception {
        var disabled = seed("disabled-" + unique + "@recovery.test", Role.HR);
        disabled.accountEnabled = false;
        users.saveAndFlush(disabled);
        var locked = seed("locked-" + unique + "@recovery.test", Role.LECTURER);
        locked.accountLocked = true;
        users.saveAndFlush(locked);
        var invalidRole = roles.saveAndFlush(new Role("UNSUPPORTED_" + unique.substring(0, 12), "Không hỗ trợ"));
        var invalid = seed("invalid-" + unique + "@recovery.test", Role.STUDENT);
        invalid.role = invalidRole;
        users.saveAndFlush(invalid);
        JsonNode baseline = body(request("unknown-" + unique + "@recovery.test").andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
        for (var account : List.of(disabled, locked, invalid)) {
            assertEquals(baseline, body(request(account.email).andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8)));
        }
        verify(mailer, never()).sendCode(anyString(), anyString());
    }

    @Test
    void emailAndCodeVerificationAllowsOnePasswordChangeWithBcryptAndNoAuthenticatedSession() throws Exception {
        String token = verifiedToken(owner.email, requestCode(owner.email));
        assertTrue(token.matches("[A-Za-z0-9_-]{43}"));
        String persisted = jdbc.queryForObject("select token_hash from password_reset_challenges where email_key = ?",
                String.class, key(owner.email));
        assertNotEquals(token, persisted);
        complete(owner.email, token, NEW_PASSWORD, NEW_PASSWORD).andExpect(status().isNoContent());
        var saved = users.findById(owner.id).orElseThrow();
        assertNotEquals(owner.passwordHash, saved.passwordHash);
        assertTrue(encoder.matches(NEW_PASSWORD, saved.passwordHash));
        assertEquals(owner.credentialsVersion + 1, saved.credentialsVersion);
        login(owner.email, PASSWORD).andExpect(status().isUnauthorized());
        login(owner.email, NEW_PASSWORD).andExpect(status().isNoContent());
        mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
        complete(owner.email, token, "AnotherPassword2026!", "AnotherPassword2026!")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("RESET_TOKEN_INVALID"));
        assertTrue(encoder.matches(NEW_PASSWORD, users.findById(owner.id).orElseThrow().passwordHash));
    }

    @Test
    void wrongCodesConsumeTheAttemptLimitAndCorrectCodeCannotBypassALockout() throws Exception {
        String code = requestCode(owner.email);
        String wrong = code.equals("000000") ? "999999" : "000000";
        for (int attempt = 1; attempt <= 5; attempt++) {
            var response = verifyCode(owner.email, wrong);
            if (attempt < 5) response.andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("RESET_CODE_INVALID"));
            else response.andExpect(status().isTooManyRequests()).andExpect(jsonPath("$.code").value("RESET_ATTEMPTS_EXCEEDED"));
            assertEquals(attempt, jdbc.queryForObject("select attempts from password_reset_challenges where email_key = ?",
                    Integer.class, key(owner.email)));
        }
        verifyCode(owner.email, code).andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("RESET_ATTEMPTS_EXCEEDED"));
        assertEquals(owner.passwordHash, users.findById(owner.id).orElseThrow().passwordHash);
    }

    @Test
    void aCodeExpiresAfterTenMinutesAndNeverProducesAResetToken() throws Exception {
        String code = requestCode(owner.email);
        jdbc.update("update password_reset_challenges set code_expires_at = ? where email_key = ?",
                Timestamp.from(Instant.now().minusSeconds(1)), key(owner.email));
        verifyCode(owner.email, code).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("RESET_CODE_EXPIRED"))
                .andExpect(jsonPath("$.resetToken").doesNotHaveJsonPath());
        assertEquals(owner.passwordHash, users.findById(owner.id).orElseThrow().passwordHash);
    }

    @Test
    void resendRequiresSixtySecondsAndReplacesThePreviousCodeAndResetToken() throws Exception {
        String firstCode = requestCode(owner.email);
        String firstToken = verifiedToken(owner.email, firstCode);
        request(owner.email).andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("RESET_RATE_LIMITED"));
        allowResend(owner.email);
        String nextCode = requestCode(owner.email);
        complete(owner.email, firstToken, NEW_PASSWORD, NEW_PASSWORD).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("RESET_TOKEN_INVALID"));
        if (!firstCode.equals(nextCode)) {
            verifyCode(owner.email, firstCode).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("RESET_CODE_INVALID"));
        }
        String nextToken = verifiedToken(owner.email, nextCode);
        assertNotEquals(firstToken, nextToken);
        complete(owner.email, nextToken, NEW_PASSWORD, NEW_PASSWORD).andExpect(status().isNoContent());
    }

    @Test
    void tokenIsBoundToItsEmailAndCannotBeGuessedOrReverifiedAfterUse() throws Exception {
        String code = requestCode(owner.email);
        String token = verifiedToken(owner.email, code);
        complete("unknown-" + unique + "@recovery.test", token, NEW_PASSWORD, NEW_PASSWORD)
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("RESET_TOKEN_INVALID"));
        complete(owner.email, "a".repeat(43), NEW_PASSWORD, NEW_PASSWORD)
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("RESET_TOKEN_INVALID"));
        complete(owner.email, token, NEW_PASSWORD, NEW_PASSWORD).andExpect(status().isNoContent());
        verifyCode(owner.email, code).andExpect(status().isBadRequest());
        assertTrue(encoder.matches(NEW_PASSWORD, users.findById(owner.id).orElseThrow().passwordHash));
    }

    @Test
    void verifiedTokensExpireAfterFiveMinutesWithoutChangingThePassword() throws Exception {
        String token = verifiedToken(owner.email, requestCode(owner.email));
        jdbc.update("update password_reset_challenges set token_expires_at = ? where email_key = ?",
                Timestamp.from(Instant.now().minusSeconds(1)), key(owner.email));
        complete(owner.email, token, NEW_PASSWORD, NEW_PASSWORD).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("RESET_TOKEN_EXPIRED"));
        assertEquals(owner.passwordHash, users.findById(owner.id).orElseThrow().passwordHash);
    }

    @Test
    void administrativeEmailRoleOrPasswordChangesInvalidateExistingRecoveryTokens() throws Exception {
        for (String changed : List.of("email", "role", "password")) {
            var account = seed(changed + "-" + unique + "@recovery.test", Role.STUDENT);
            String originalEmail = account.email;
            String token = verifiedToken(originalEmail, requestCode(originalEmail));
            if (changed.equals("email")) account.email = "new-" + originalEmail;
            if (changed.equals("role")) {
                account.role = roles.findByCode(Role.HR).orElseThrow();
                account.credentialsVersion++;
            }
            if (changed.equals("password")) account.passwordHash = encoder.encode("ExternalPassword2026!");
            users.saveAndFlush(account);
            complete(originalEmail, token, NEW_PASSWORD, NEW_PASSWORD).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("RESET_TOKEN_INVALID"));
            assertEquals(account.passwordHash, users.findById(account.id).orElseThrow().passwordHash);
        }
    }

    @Test
    void invalidPasswordsCanBeCorrectedWithoutConsumingTheValidToken() throws Exception {
        String token = verifiedToken(owner.email, requestCode(owner.email));
        for (var invalid : List.of(Map.entry("short", "short"), Map.entry(NEW_PASSWORD, "Mismatched2026!"),
                Map.entry("a".repeat(73), "a".repeat(73)), Map.entry("á".repeat(37), "á".repeat(37)))) {
            complete(owner.email, token, invalid.getKey(), invalid.getValue()).andExpect(status().isBadRequest());
            assertEquals(owner.passwordHash, users.findById(owner.id).orElseThrow().passwordHash);
        }
        complete(owner.email, token, NEW_PASSWORD, NEW_PASSWORD).andExpect(status().isNoContent());
        request("not-an-email").andExpect(status().isBadRequest());
        verifyCode(owner.email, "12345").andExpect(status().isBadRequest());
        verifyCode(owner.email, "12345x").andExpect(status().isBadRequest());
    }

    @Test
    void perEmailAndPerIpLimitsBoundResendsAndAccountEnumerationAttempts() throws Exception {
        for (int count = 0; count < 5; count++) {
            if (count > 0) allowResend(owner.email);
            requestCode(owner.email);
        }
        allowResend(owner.email);
        request(owner.email).andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("RESET_RATE_LIMITED"));
        // Start a separate client window so the per-IP check is independent of the email limit.
        jdbc.update("delete from password_reset_rate_limits");
        for (int count = 0; count < 20; count++) {
            request("unknown-" + count + "-" + unique + "@recovery.test").andExpect(status().isOk());
        }
        request("unknown-final-" + unique + "@recovery.test").andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("RESET_RATE_LIMITED"));
        verify(mailer, times(5)).sendCode(eq(owner.email), anyString());
    }

    @Test
    void completingRecoveryRevokesAnExistingLoginSession() throws Exception {
        var login = login(owner.email, PASSWORD).andExpect(status().isNoContent()).andReturn();
        var session = (MockHttpSession) login.getRequest().getSession(false);
        assertNotNull(session);
        mvc.perform(get("/api/auth/me").session(session)).andExpect(status().isOk());
        String token = verifiedToken(owner.email, requestCode(owner.email));
        complete(owner.email, token, NEW_PASSWORD, NEW_PASSWORD).andExpect(status().isNoContent());
        mvc.perform(get("/api/auth/me").session(session)).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("SESSION_EXPIRED"));
    }

    @Test
    void missingMailConfigurationFailsClearlyAndRecoveryEndpointsStillRequireCsrf() throws Exception {
        doThrow(new PasswordResetException(HttpStatus.SERVICE_UNAVAILABLE, "MAIL_NOT_CONFIGURED",
                "Chưa cấu hình gửi email", 0)).when(mailer).requireConfigured();
        request(owner.email).andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("MAIL_NOT_CONFIGURED"));
        verify(mailer, never()).sendCode(anyString(), anyString());
        for (var entry : List.of(Map.entry("/request", Map.of("email", owner.email)),
                Map.entry("/verify", Map.of("email", owner.email, "code", "123456")),
                Map.entry("/complete", Map.of("email", owner.email, "resetToken", "a".repeat(43),
                        "newPassword", NEW_PASSWORD, "confirmPassword", NEW_PASSWORD)))) {
            mvc.perform(post(API + entry.getKey()).contentType("application/json")
                            .content(json.writeValueAsString(entry.getValue())))
                    .andExpect(status().isForbidden());
        }
        assertEquals(owner.passwordHash, users.findById(owner.id).orElseThrow().passwordHash);
    }

    private User seed(String email, String code) {
        var account = new User();
        account.email = email;
        account.fullName = "Tài khoản khôi phục";
        account.passwordHash = encoder.encode(PASSWORD);
        account.role = roles.findByCode(code).orElseThrow();
        return users.saveAndFlush(account);
    }

    private ResultActions request(String email) throws Exception {
        return postJson("/request", Map.of("email", email));
    }

    private String requestCode(String email) throws Exception {
        request(email).andExpect(status().isOk());
        return deliveredCode(email);
    }

    private String deliveredCode(String email) {
        int expected = deliveryCounts.merge(email, 1, Integer::sum);
        var codes = ArgumentCaptor.forClass(String.class);
        verify(mailer, timeout(3000).times(expected)).sendCode(eq(email), codes.capture());
        return codes.getAllValues().get(expected - 1);
    }

    private ResultActions verifyCode(String email, String code) throws Exception {
        return postJson("/verify", Map.of("email", email, "code", code));
    }

    private String verifiedToken(String email, String code) throws Exception {
        var result = verifyCode(email, code).andExpect(status().isOk())
                .andExpect(jsonPath("$.expiresInSeconds").value(300))
                .andExpect(jsonPath("$.code").doesNotHaveJsonPath()).andReturn();
        return body(result.getResponse().getContentAsString(StandardCharsets.UTF_8)).get("resetToken").asText();
    }

    private ResultActions complete(String email, String token, String password, String confirmation) throws Exception {
        return postJson("/complete", Map.of("email", email, "resetToken", token,
                "newPassword", password, "confirmPassword", confirmation));
    }

    private ResultActions postJson(String path, Map<String, ?> payload) throws Exception {
        return mvc.perform(post(API + path).with(csrf()).contentType("application/json")
                .content(json.writeValueAsString(payload)));
    }

    private void allowResend(String email) throws Exception {
        jdbc.update("update password_reset_challenges set requested_at = ? where email_key = ?",
                Timestamp.from(Instant.now().minusSeconds(61)), key(email));
    }

    private ResultActions login(String email, String password) throws Exception {
        return mvc.perform(post("/api/auth/login").with(csrf()).param("username", email).param("password", password));
    }

    private String key(String email) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(
                email.strip().toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8)));
    }

    private JsonNode body(String value) throws Exception { return json.readTree(value); }
}

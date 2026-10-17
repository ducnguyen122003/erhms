package com.erhms;

import com.erhms.role.entity.Role;
import com.erhms.role.repository.RoleRepository;
import com.erhms.user.dto.PasswordChangeInput;
import com.erhms.user.entity.User;
import com.erhms.user.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:erhms_password_change_test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PasswordChangeIntegrationTest {
    private static final String CHANGE = "/api/auth/change-password";
    private static final String OLD_PASSWORD = "CurrentPassword@2026";
    private static final String NEW_PASSWORD = "NewPassword@2026";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserRepository users;
    @Autowired RoleRepository roles;
    @Autowired PasswordEncoder encoder;

    private User owner;
    private User other;

    @BeforeEach
    void setup() {
        String suffix = UUID.randomUUID() + "@password.test";
        owner = seedUser("owner-" + suffix);
        other = seedUser("other-" + suffix);
    }

    @Test
    void changesOnlyCurrentUsersEncodedPasswordWithoutTrimmingAndKeepsSessionAuthenticated() throws Exception {
        String newPassword = "  New Password@2026  ";
        var session = login(owner.email, OLD_PASSWORD);
        var originalContext = session.getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
        var payload = input(OLD_PASSWORD, newPassword);
        payload.put("id", other.id);
        payload.put("userId", other.id);
        payload.put("email", other.email);
        payload.put("role", Role.SERVICE_MANAGER);
        payload.put("roleId", other.getRoleId());
        payload.put("passwordHash", "attacker-hash");

        change(session, payload).andExpect(status().isNoContent()).andExpect(content().string(""));

        var saved = users.findByEmail(owner.email).orElseThrow();
        assertNotEquals(owner.passwordHash, saved.passwordHash);
        assertNotEquals(newPassword, saved.passwordHash);
        assertTrue(encoder.matches(newPassword, saved.passwordHash));
        assertFalse(encoder.matches(newPassword.strip(), saved.passwordHash));
        assertFalse(encoder.matches(OLD_PASSWORD, saved.passwordHash));
        assertEquals(owner.fullName, saved.fullName);
        assertEquals(owner.phone, saved.phone);
        assertEquals(owner.getRoleId(), saved.getRoleId());
        assertEquals(other.passwordHash, users.findById(other.id).orElseThrow().passwordHash);
        assertSame(originalContext, session.getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY));

        mvc.perform(get("/api/auth/me").session(session)).andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(owner.id))
                .andExpect(jsonPath("$.email").value(owner.email))
                .andExpect(jsonPath("$.password").doesNotHaveJsonPath())
                .andExpect(jsonPath("$.passwordHash").doesNotHaveJsonPath())
                .andExpect(jsonPath("$.oldPassword").doesNotHaveJsonPath())
                .andExpect(jsonPath("$.newPassword").doesNotHaveJsonPath())
                .andExpect(jsonPath("$.confirmPassword").doesNotHaveJsonPath());
        attemptLogin(owner.email, OLD_PASSWORD).andExpect(status().isUnauthorized());
        login(owner.email, newPassword);
    }

    @Test
    void wrongOldPasswordCannotChangeStoredHash() throws Exception {
        var session = login(owner.email, OLD_PASSWORD);
        change(session, input("WrongPassword@2026", NEW_PASSWORD)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Mật khẩu hiện tại không đúng"));
        assertPasswordUnchanged();
    }

    @Test
    void confirmationMustMatchExactly() throws Exception {
        var session = login(owner.email, OLD_PASSWORD);
        var payload = input(OLD_PASSWORD, NEW_PASSWORD);
        payload.put("confirmPassword", NEW_PASSWORD + " ");
        change(session, payload).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Mật khẩu xác nhận không khớp với mật khẩu mới"));
        assertPasswordUnchanged();
    }

    @Test
    void allPasswordFieldsAreRequiredWithoutExposingTheirValuesInErrors() throws Exception {
        var session = login(owner.email, OLD_PASSWORD);
        for (String field : List.of("oldPassword", "newPassword", "confirmPassword")) {
            for (String invalid : new String[]{null, "", "   "}) {
                var payload = input(OLD_PASSWORD, NEW_PASSWORD);
                payload.put(field, invalid);
                var result = change(session, payload).andExpect(status().isBadRequest()).andReturn();
                String body = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
                assertFalse(body.contains(OLD_PASSWORD));
                assertFalse(body.contains(NEW_PASSWORD));
                assertPasswordUnchanged();
            }
            var missing = input(OLD_PASSWORD, NEW_PASSWORD);
            missing.remove(field);
            change(session, missing).andExpect(status().isBadRequest());
            assertPasswordUnchanged();
        }
    }

    @Test
    void newPasswordRequiresAtLeastEightCharactersIncludingUnicodeCodePoints() throws Exception {
        var session = login(owner.email, OLD_PASSWORD);
        for (String invalid : List.of("1234567", "😀".repeat(4))) {
            change(session, input(OLD_PASSWORD, invalid)).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("Mật khẩu mới phải có ít nhất 8 ký tự"));
            assertPasswordUnchanged();
        }
    }

    @Test
    void eightCharactersAndExactlySeventyTwoUtf8BytesAreAccepted() throws Exception {
        var session = login(owner.email, OLD_PASSWORD);
        String currentPassword = OLD_PASSWORD;
        for (String valid : List.of("12345678", "a".repeat(72), "ế".repeat(24), "😀".repeat(18))) {
            change(session, input(currentPassword, valid)).andExpect(status().isNoContent());
            assertTrue(encoder.matches(valid, users.findById(owner.id).orElseThrow().passwordHash));
            currentPassword = valid;
        }
        login(owner.email, currentPassword);
    }

    @Test
    void rejectsPasswordsAboveBcryptByteLimitBeforeEncodingOrMatching() throws Exception {
        var session = login(owner.email, OLD_PASSWORD);
        for (String oversized : List.of("a".repeat(73), "ế".repeat(25), "😀".repeat(19))) {
            change(session, input(OLD_PASSWORD, oversized)).andExpect(status().isBadRequest());
            change(session, input(oversized, NEW_PASSWORD)).andExpect(status().isBadRequest());
            var payload = input(OLD_PASSWORD, NEW_PASSWORD);
            payload.put("confirmPassword", oversized);
            change(session, payload).andExpect(status().isBadRequest());
            assertPasswordUnchanged();
        }
    }

    @Test
    void doesNotImposeExtraCompositionRules() throws Exception {
        var session = login(owner.email, OLD_PASSWORD);
        change(session, input(OLD_PASSWORD, "abcdefgh")).andExpect(status().isNoContent());
        login(owner.email, "abcdefgh");
    }

    @Test
    void requiresAuthenticationEvenWithValidCsrf() throws Exception {
        var result = mvc.perform(get("/api/auth/csrf")).andExpect(status().isOk()).andReturn();
        var session = (MockHttpSession) result.getRequest().getSession(false);
        var token = json.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
        mvc.perform(post(CHANGE).session(session)
                        .header(token.get("headerName").asText(), token.get("token").asText())
                        .contentType("application/json").content(json.writeValueAsString(input(OLD_PASSWORD, NEW_PASSWORD))))
                .andExpect(status().isUnauthorized());
        assertPasswordUnchanged();
    }

    @Test
    void requiresCsrfForAuthenticatedRequests() throws Exception {
        var session = login(owner.email, OLD_PASSWORD);
        mvc.perform(post(CHANGE).session(session).contentType("application/json")
                        .content(json.writeValueAsString(input(OLD_PASSWORD, NEW_PASSWORD))))
                .andExpect(status().isForbidden());
        assertPasswordUnchanged();
    }

    @Test
    void simultaneousChangesCannotBothReuseTheSameOldPassword() throws Exception {
        var session = login(owner.email, OLD_PASSWORD);
        var token = csrf(session);
        var executor = Executors.newFixedThreadPool(2);
        var start = new CountDownLatch(1);
        try {
            var first = executor.submit(() -> {
                start.await();
                return change(session, token, input(OLD_PASSWORD, "FirstNewPassword@2026"))
                        .andReturn().getResponse().getStatus();
            });
            var second = executor.submit(() -> {
                start.await();
                return change(session, token, input(OLD_PASSWORD, "SecondNewPassword@2026"))
                        .andReturn().getResponse().getStatus();
            });
            start.countDown();
            var statuses = List.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS));
            assertTrue(statuses.contains(204), statuses.toString());
            assertTrue(statuses.contains(400), statuses.toString());
            var hash = users.findById(owner.id).orElseThrow().passwordHash;
            assertNotEquals(encoder.matches("FirstNewPassword@2026", hash),
                    encoder.matches("SecondNewPassword@2026", hash));
            assertFalse(encoder.matches(OLD_PASSWORD, hash));
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void passwordDtoStringNeverIncludesPlaintextForRequestDebugLogging() {
        var input = new PasswordChangeInput(OLD_PASSWORD, NEW_PASSWORD, NEW_PASSWORD);
        assertEquals("PasswordChangeInput[REDACTED]", input.toString());
    }

    private User seedUser(String email) {
        var user = new User();
        user.email = email;
        user.fullName = "Password Change User";
        user.phone = "0912345678";
        user.passwordHash = encoder.encode(OLD_PASSWORD);
        user.role = roles.findByCode(Role.USER).orElseThrow();
        return users.saveAndFlush(user);
    }

    private void assertPasswordUnchanged() {
        assertEquals(owner.passwordHash, users.findById(owner.id).orElseThrow().passwordHash);
        assertEquals(other.passwordHash, users.findById(other.id).orElseThrow().passwordHash);
    }

    private Map<String, Object> input(String oldPassword, String newPassword) {
        return new HashMap<>(Map.of("oldPassword", oldPassword, "newPassword", newPassword,
                "confirmPassword", newPassword));
    }

    private MockHttpSession login(String email, String password) throws Exception {
        var result = attemptLogin(email, password).andExpect(status().isNoContent()).andReturn();
        var session = (MockHttpSession) result.getRequest().getSession(false);
        assertNotNull(session);
        return session;
    }

    private ResultActions attemptLogin(String email, String password) throws Exception {
        var result = mvc.perform(get("/api/auth/csrf")).andExpect(status().isOk()).andReturn();
        var session = (MockHttpSession) result.getRequest().getSession(false);
        var token = json.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
        return mvc.perform(post("/api/auth/login").session(session)
                .header(token.get("headerName").asText(), token.get("token").asText())
                .param("username", email).param("password", password));
    }

    private JsonNode csrf(MockHttpSession session) throws Exception {
        var result = mvc.perform(get("/api/auth/csrf").session(session)).andExpect(status().isOk()).andReturn();
        return json.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private ResultActions change(MockHttpSession session, Map<String, Object> payload) throws Exception {
        return change(session, csrf(session), payload);
    }

    private ResultActions change(MockHttpSession session, JsonNode token, Map<String, Object> payload) throws Exception {
        return mvc.perform(post(CHANGE).session(session)
                .header(token.get("headerName").asText(), token.get("token").asText())
                .contentType("application/json").content(json.writeValueAsString(payload)));
    }
}

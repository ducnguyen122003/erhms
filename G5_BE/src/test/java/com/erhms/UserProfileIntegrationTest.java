package com.erhms;

import com.erhms.role.entity.Role;
import com.erhms.role.repository.RoleRepository;
import com.erhms.user.entity.User;
import com.erhms.user.repository.UserRepository;
import com.erhms.user.security.UserPrincipal;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.context.SecurityContext;
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

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:erhms_user_profile_test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UserProfileIntegrationTest {
    private static final String ME = "/api/auth/me";
    private static final String PASSWORD = "TestPassword@2026";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserRepository users;
    @Autowired RoleRepository roles;
    @Autowired PasswordEncoder encoder;

    private User owner;
    private User other;

    @BeforeEach
    void setup() {
        String suffix = UUID.randomUUID() + "@profile.test";
        owner = seedUser("owner-" + suffix, "Original Owner", Role.USER);
        other = seedUser("other-" + suffix, "Other Owner", Role.SERVICE_MANAGER);
    }

    @Test
    void readsCurrentAccountIncludingNewNullablePhoneWithoutPassword() throws Exception {
        var session = login(owner.email);
        mvc.perform(get(ME).session(session)).andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(owner.id))
                .andExpect(jsonPath("$.email").value(owner.email))
                .andExpect(jsonPath("$.fullName").value(owner.fullName))
                .andExpect(jsonPath("$.role").value(Role.USER))
                .andExpect(jsonPath("$.phone").isEmpty())
                .andExpect(jsonPath("$.password").doesNotHaveJsonPath())
                .andExpect(jsonPath("$.passwordHash").doesNotHaveJsonPath());
    }

    @Test
    void updatesEditableFieldsPersistsThemAndKeepsEmailAndSessionUnchanged() throws Exception {
        String passwordHash = owner.passwordHash;
        Long roleId = owner.getRoleId();
        var session = login(owner.email);
        var originalContext = (SecurityContext) session.getAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
        var originalDetails = originalContext.getAuthentication().getDetails();

        var payload = input();
        payload.put("email", "attacker@example.com");
        payload.put("fullName", "  Nguyễn Văn An  ");
        payload.put("phone", " +84 (912) 345-678 ");
        update(session, payload).andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(owner.id))
                .andExpect(jsonPath("$.email").value(owner.email))
                .andExpect(jsonPath("$.fullName").value("Nguyễn Văn An"))
                .andExpect(jsonPath("$.phone").value("+84912345678"));

        var saved = users.findByEmail(owner.email).orElseThrow();
        assertEquals(owner.id, saved.id);
        assertEquals("Nguyễn Văn An", saved.fullName);
        assertEquals("+84912345678", saved.phone);
        assertEquals(passwordHash, saved.passwordHash);
        assertEquals(roleId, saved.getRoleId());
        assertTrue(users.findByEmail("attacker@example.com").isEmpty());

        var context = (SecurityContext) session.getAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
        assertEquals(owner.email, context.getAuthentication().getName());
        assertSame(originalContext, context);
        assertEquals(originalDetails, context.getAuthentication().getDetails());
        assertNull(context.getAuthentication().getCredentials());
        var principal = (org.springframework.security.core.userdetails.UserDetails)
                context.getAuthentication().getPrincipal();
        assertNull(principal.getPassword());
        mvc.perform(get(ME).session(session)).andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(owner.email));
        mvc.perform(get("/api/recruitment-requests").session(session)).andExpect(status().isOk());

        var token = csrf(session);
        mvc.perform(post("/api/auth/logout").session(session)
                        .header(token.get("headerName").asText(), token.get("token").asText()))
                .andExpect(status().isNoContent());
        assertTrue(session.isInvalid());
        var nextSession = login(owner.email);
        mvc.perform(get(ME).session(nextSession)).andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(owner.id));
        attemptLogin("attacker@example.com").andExpect(status().isUnauthorized());
    }

    @Test
    void ignoresEmailChangesIncludingAnAddressBelongingToAnotherAccount() throws Exception {
        other.email = other.email.toUpperCase(java.util.Locale.ROOT);
        users.saveAndFlush(other);
        var session = login(owner.email);
        var payload = input();
        payload.put("email", " " + other.email.toLowerCase(java.util.Locale.ROOT) + " ");
        update(session, payload).andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(owner.email));
        assertEquals(owner.email, users.findById(owner.id).orElseThrow().email);
        assertEquals("Updated Owner", users.findById(owner.id).orElseThrow().fullName);
        assertUnchanged(other);
        mvc.perform(get(ME).session(session)).andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(owner.email));
    }

    @Test
    void secondSessionKeepsItsOriginalAccountWhenAnotherAccountReusesTheOldEmail() throws Exception {
        String originalEmail = owner.email;
        String renamedEmail = "renamed-" + originalEmail;
        var secondOwnerSession = login(originalEmail);
        // An external administrative change cannot redirect an existing session to another account.
        owner.email = renamedEmail;
        users.saveAndFlush(owner);
        other.email = originalEmail;
        users.saveAndFlush(other);
        assertEquals(other.id, users.findByEmail(originalEmail).orElseThrow().id);

        var context = (SecurityContext) secondOwnerSession.getAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
        var principal = assertInstanceOf(UserPrincipal.class, context.getAuthentication().getPrincipal());
        assertEquals(owner.id, principal.getUserId());
        assertEquals(originalEmail, principal.getUsername());
        assertNull(principal.getPassword());
        mvc.perform(get(ME).session(secondOwnerSession)).andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(owner.id))
                .andExpect(jsonPath("$.email").value(renamedEmail))
                .andExpect(jsonPath("$.role").value(Role.USER));
        mvc.perform(get("/api/recruitment-requests").session(secondOwnerSession))
                .andExpect(status().isOk());

        var payload = input();
        payload.put("fullName", "Updated Through Second Session");
        update(secondOwnerSession, payload).andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(owner.id));
        assertEquals("Updated Through Second Session", users.findByEmail(renamedEmail).orElseThrow().fullName);
        var savedOther = users.findByEmail(originalEmail).orElseThrow();
        assertEquals(other.id, savedOther.id);
        assertEquals("Other Owner", savedOther.fullName);
        assertEquals(Role.SERVICE_MANAGER, savedOther.getRoleCode());
    }

    @Test
    void emailIsNotRequiredAndBlankOrMissingPhoneClearsPhone() throws Exception {
        var session = login(owner.email);
        var payload = input();
        payload.remove("email");
        update(session, payload).andExpect(status().isOk());
        assertEquals("0912345678", users.findById(owner.id).orElseThrow().phone);
        payload.put("phone", "  ");
        update(session, payload).andExpect(status().isOk()).andExpect(jsonPath("$.phone").isEmpty());
        assertNull(users.findById(owner.id).orElseThrow().phone);
        payload.put("phone", "0912345678");
        update(session, payload).andExpect(status().isOk());
        payload.remove("phone");
        update(session, payload).andExpect(status().isOk()).andExpect(jsonPath("$.phone").isEmpty());
        assertNull(users.findById(owner.id).orElseThrow().phone);
    }

    @Test
    void ignoresAccountIdentityRoleAndPasswordFromInputAndOnlyUpdatesCurrentUser() throws Exception {
        var session = login(owner.email);
        var payload = input();
        payload.put("id", other.id);
        payload.put("userId", other.id);
        payload.put("role", Role.SERVICE_MANAGER);
        payload.put("roleId", other.getRoleId());
        payload.put("passwordHash", "attacker-hash");
        payload.put("password", "AttackerPassword");
        payload.put("oldPassword", PASSWORD);
        payload.put("newPassword", "AttackerPassword");
        payload.put("confirmPassword", "AttackerPassword");
        update(session, payload).andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(owner.id))
                .andExpect(jsonPath("$.role").value(Role.USER))
                .andExpect(jsonPath("$.passwordHash").doesNotHaveJsonPath());
        var saved = users.findByEmail(owner.email).orElseThrow();
        assertEquals(owner.passwordHash, saved.passwordHash);
        assertEquals(owner.getRoleId(), saved.getRoleId());
        assertEquals("Updated Owner", saved.fullName);
        assertUnchanged(other);
    }

    @Test
    void csrfIsRequiredForProfileUpdates() throws Exception {
        var session = login(owner.email);
        mvc.perform(put(ME).session(session).contentType("application/json")
                        .content(json.writeValueAsString(input())))
                .andExpect(status().isForbidden());
        assertUnchanged(owner);
    }

    @Test
    void invalidFieldsNeverChangeProfile() throws Exception {
        var session = login(owner.email);
        for (var invalid : List.of(
                Map.entry("fullName", " "),
                Map.entry("fullName", "A"),
                Map.entry("fullName", "A".repeat(121)),
                Map.entry("phone", "09123abc78"),
                Map.entry("phone", "091234567"),
                Map.entry("phone", "+849123456789"),
                Map.entry("phone", "0".repeat(21)))) {
            var payload = input();
            payload.put(invalid.getKey(), invalid.getValue());
            update(session, payload).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fields." + invalid.getKey()).exists());
            assertUnchanged(owner);
        }
        for (String required : List.of("fullName")) {
            var payload = input();
            payload.remove(required);
            update(session, payload).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fields." + required).exists());
            assertUnchanged(owner);
        }
        mvc.perform(get(ME).session(session)).andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(owner.email));
    }

    @Test
    void anonymousCannotReadOrUpdateProfileEvenWithValidCsrf() throws Exception {
        mvc.perform(get(ME)).andExpect(status().isUnauthorized());
        var tokenResult = mvc.perform(get("/api/auth/csrf")).andExpect(status().isOk()).andReturn();
        var session = (MockHttpSession) tokenResult.getRequest().getSession(false);
        var token = json.readTree(tokenResult.getResponse().getContentAsString(StandardCharsets.UTF_8));
        mvc.perform(put(ME).session(session)
                        .header(token.get("headerName").asText(), token.get("token").asText())
                        .contentType("application/json").content(json.writeValueAsString(input())))
                .andExpect(status().isUnauthorized());
        assertUnchanged(owner);
    }

    private User seedUser(String email, String fullName, String role) {
        var user = new User();
        user.email = email;
        user.fullName = fullName;
        user.passwordHash = encoder.encode(PASSWORD);
        user.role = roles.findByCode(role).orElseThrow();
        return users.saveAndFlush(user);
    }

    private void assertUnchanged(User original) {
        var saved = users.findByEmail(original.email).orElseThrow();
        assertEquals(original.id, saved.id);
        assertEquals(original.fullName, saved.fullName);
        assertEquals(original.phone, saved.phone);
        assertEquals(original.passwordHash, saved.passwordHash);
        assertEquals(original.getRoleId(), saved.getRoleId());
    }

    private Map<String, Object> input() {
        return new HashMap<>(Map.of("email", owner.email, "fullName", "Updated Owner", "phone", "0912345678"));
    }

    private MockHttpSession login(String email) throws Exception {
        var result = attemptLogin(email).andExpect(status().isNoContent()).andReturn();
        var session = (MockHttpSession) result.getRequest().getSession(false);
        assertNotNull(session);
        return session;
    }

    private ResultActions attemptLogin(String email) throws Exception {
        var result = mvc.perform(get("/api/auth/csrf")).andExpect(status().isOk()).andReturn();
        var session = (MockHttpSession) result.getRequest().getSession(false);
        var token = json.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
        return mvc.perform(post("/api/auth/login").session(session)
                .header(token.get("headerName").asText(), token.get("token").asText())
                .param("username", email).param("password", PASSWORD));
    }

    private JsonNode csrf(MockHttpSession session) throws Exception {
        var result = mvc.perform(get("/api/auth/csrf").session(session)).andExpect(status().isOk()).andReturn();
        return json.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private ResultActions update(MockHttpSession session, Map<String, Object> input) throws Exception {
        var token = csrf(session);
        return mvc.perform(put(ME).session(session)
                .header(token.get("headerName").asText(), token.get("token").asText())
                .contentType("application/json").content(json.writeValueAsString(input)));
    }
}

package com.erhms;

import com.erhms.admin.dto.AdminAccountInput;
import com.erhms.role.entity.Role;
import com.erhms.role.repository.RoleRepository;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
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
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:erhms_admin_accounts_test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminAccountsIntegrationTest {
    private static final String API = "/api/admin/users";
    private static final String PASSWORD = "TestPassword@2026";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserRepository users;
    @Autowired RoleRepository roles;
    @Autowired PasswordEncoder encoder;
    @Autowired JdbcTemplate jdbc;

    private User administrator;
    private User target;
    private String unique;

    @BeforeEach
    void setup() {
        unique = UUID.randomUUID().toString();
        administrator = seed("admin-" + unique + "@accounts.test", "Quản trị viên", Role.ADMIN);
        target = seed("target-" + unique + "@accounts.test", "Nguyễn Văn An", Role.STUDENT);
    }

    @Test
    void onlyAnActualEnabledDatabaseAdministratorMayUseEveryEndpoint() throws Exception {
        mvc.perform(get(API)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/roles")).andExpect(status().isUnauthorized());
        mvc.perform(get(API + "/" + target.id)).andExpect(status().isUnauthorized());
        mvc.perform(delete(API + "/" + target.id).with(csrf())).andExpect(status().isUnauthorized());
        mvc.perform(patch(API + "/" + target.id + "/status").with(csrf()).contentType("application/json")
                        .content("{\"accountEnabled\":false}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(patch(API + "/" + target.id + "/lock").with(csrf()).contentType("application/json")
                        .content("{\"accountLocked\":true}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post(API).with(csrf()).contentType("application/json").content(json.writeValueAsString(input())))
                .andExpect(status().isUnauthorized());
        mvc.perform(put(API + "/" + target.id).with(csrf()).contentType("application/json")
                        .content(json.writeValueAsString(input())))
                .andExpect(status().isUnauthorized());
        for (String path : List.of(API, "/api/admin/roles", API + "/" + target.id)) {
            mvc.perform(get(path).with(user(target.email).roles("ADMIN"))).andExpect(status().isForbidden());
        }
        createAs(target, input()).andExpect(status().isForbidden());
        editAs(target, target.id, input()).andExpect(status().isForbidden());
        deleteAs(target, target.id).andExpect(status().isForbidden());
        statusAs(target, target.id, false).andExpect(status().isForbidden());
        lockAs(target, target.id, true).andExpect(status().isForbidden());
        administrator.accountLocked = true;
        users.saveAndFlush(administrator);
        mvc.perform(get(API).with(user(administrator.email).roles("ADMIN"))).andExpect(status().isForbidden());
        administrator.accountLocked = false;
        administrator.accountEnabled = false;
        users.saveAndFlush(administrator);
        mvc.perform(get(API).with(user(administrator.email).roles("ADMIN"))).andExpect(status().isForbidden());
    }

    @Test
    void staleAdministratorAuthoritiesCannotBypassTheCurrentDatabaseRole() throws Exception {
        var claimedAdministrator = user(administrator.email).roles("ADMIN");
        administrator.role = roles.findByCode(Role.HR).orElseThrow();
        users.saveAndFlush(administrator);
        mvc.perform(get(API).with(claimedAdministrator)).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/roles").with(claimedAdministrator)).andExpect(status().isForbidden());
        createAs(administrator, input()).andExpect(status().isForbidden());
        editAs(administrator, target.id, input()).andExpect(status().isForbidden());
        statusAs(administrator, target.id, false).andExpect(status().isForbidden());
        lockAs(administrator, target.id, true).andExpect(status().isForbidden());
    }

    @Test
    void rolesReturnOnlyTheSevenFixedDatabaseIdAndCodePairsInOrder() throws Exception {
        var extra = new Role("UNSUPPORTED_" + unique.substring(0, 12), "Vai trò ngoài danh sách");
        extra = roles.saveAndFlush(extra);
        try {
            var result = mvc.perform(get("/api/admin/roles").with(user(administrator.email).roles("ADMIN")))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(7)).andReturn();
            JsonNode found = body(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
            String[] codes = {Role.ADMIN, Role.LECTURER, Role.STUDENT, Role.ACCOUNTANT, Role.IT_SUPPORT, Role.MANAGER, Role.HR};
            for (int index = 0; index < codes.length; index++) {
                assertEquals(index + 1, found.get(index).get("id").asLong());
                assertEquals(codes[index], found.get(index).get("code").asText());
                assertFalse(found.get(index).get("name").asText().isBlank());
            }
        } finally {
            roles.deleteById(extra.id);
        }
    }

    @Test
    void createsAnAccountWithNormalizedFieldsAndTheSuppliedBcryptPasswordWithoutExposingSecrets() throws Exception {
        var payload = input();
        payload.put("email", "  Created-" + unique.toUpperCase(Locale.ROOT) + "@ACCOUNTS.TEST  ");
        payload.put("fullName", "  Lê Thị Hương  ");
        payload.put("phone", " +84 (912) 345-678 ");
        payload.put("roleId", 4);
        payload.put("id", target.id);
        payload.put("password", "ChosenPassword@2026");
        payload.put("confirmPassword", "ChosenPassword@2026");
        payload.put("passwordHash", "attacker-hash");
        payload.put("accountEnabled", false);
        payload.put("accountLocked", true);
        var response = createAs(administrator, payload).andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("created-" + unique + "@accounts.test"))
                .andExpect(jsonPath("$.fullName").value("Lê Thị Hương"))
                .andExpect(jsonPath("$.phone").value("+84912345678"))
                .andExpect(jsonPath("$.roleId").value(4)).andExpect(jsonPath("$.role").value(Role.ACCOUNTANT))
                .andExpect(jsonPath("$.accountEnabled").value(true)).andExpect(jsonPath("$.accountLocked").value(false))
                .andExpect(jsonPath("$.password").doesNotHaveJsonPath())
                .andExpect(jsonPath("$.confirmPassword").doesNotHaveJsonPath())
                .andExpect(jsonPath("$.passwordHash").doesNotHaveJsonPath()).andReturn();
        Long id = body(response.getResponse().getContentAsString(StandardCharsets.UTF_8)).get("id").asLong();
        assertNotEquals(target.id, id);
        User created = users.findWithRoleById(id).orElseThrow();
        assertTrue(created.passwordHash.startsWith("$2"));
        assertEquals(60, created.passwordHash.length());
        assertTrue(encoder.matches("ChosenPassword@2026", created.passwordHash));
        for (String guess : List.of(PASSWORD, "123456", "ChangeMe123!", created.email)) {
            assertFalse(encoder.matches(guess, created.passwordHash));
        }
        login(created.email, "ChosenPassword@2026").andExpect(status().isNoContent());
        AdminAccountInput dto = json.convertValue(payload, AdminAccountInput.class);
        assertEquals("AdminAccountInput[REDACTED]", dto.toString());
        assertFalse(json.writeValueAsString(dto).contains("ChosenPassword@2026"));
        var second = input();
        second.put("email", "second-" + unique + "@accounts.test");
        createAs(administrator, second).andExpect(status().isCreated());
        assertNotEquals(created.passwordHash, users.findByEmail(second.get("email").toString()).orElseThrow().passwordHash);
    }

    @Test
    void editChangesOnlyRequestedAccountFieldsAndRevokesCredentialsOnEmailOrRoleChange() throws Exception {
        target.accountEnabled = false;
        target.accountLocked = true;
        users.saveAndFlush(target);
        String originalHash = target.passwordHash;
        var payload = input();
        payload.put("email", "updated-" + unique + "@accounts.test");
        payload.put("roleId", 2);
        payload.put("passwordHash", "attacker-hash");
        payload.remove("password");
        payload.remove("confirmPassword");
        payload.put("accountEnabled", true);
        payload.put("accountLocked", false);
        editAs(administrator, target.id, payload).andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(target.id)).andExpect(jsonPath("$.role").value(Role.LECTURER))
                .andExpect(jsonPath("$.accountEnabled").value(false)).andExpect(jsonPath("$.accountLocked").value(true))
                .andExpect(jsonPath("$.passwordHash").doesNotHaveJsonPath());
        User saved = users.findWithRoleById(target.id).orElseThrow();
        assertEquals(originalHash, saved.passwordHash);
        assertEquals("Updated Account", saved.fullName);
        assertEquals("0912345678", saved.phone);
        assertEquals(target.credentialsVersion + 1, saved.credentialsVersion);
        assertEquals(administrator.email, users.findById(administrator.id).orElseThrow().email);
    }

    @Test
    void aNameOrPhoneEditKeepsThePasswordRoleAndCredentialVersion() throws Exception {
        var payload = input();
        payload.put("email", target.email);
        payload.put("roleId", target.getRoleId());
        payload.put("phone", " ");
        payload.put("password", "");
        payload.put("confirmPassword", "");
        editAs(administrator, target.id, payload).andExpect(status().isOk()).andExpect(jsonPath("$.phone").isEmpty());
        User saved = users.findWithRoleById(target.id).orElseThrow();
        assertEquals(target.passwordHash, saved.passwordHash);
        assertEquals(target.credentialsVersion, saved.credentialsVersion);
        assertEquals(target.getRoleId(), saved.getRoleId());
    }

    @Test
    void normalizedNewAccountsCanLogInWithTrimmedMixedCaseEmail() throws Exception {
        var payload = input();
        payload.put("email", "  Created-" + unique.toUpperCase(Locale.ROOT) + "@ACCOUNTS.TEST  ");
        payload.put("roleId", 7);
        var response = createAs(administrator, payload).andExpect(status().isCreated()).andReturn();
        long id = body(response.getResponse().getContentAsString(StandardCharsets.UTF_8)).get("id").asLong();
        var created = users.findById(id).orElseThrow();
        var loggedIn = mvc.perform(post("/api/auth/login").with(csrf())
                        .param("username", "  " + created.email.toUpperCase(Locale.ROOT) + "  ")
                        .param("password", PASSWORD))
                .andExpect(status().isNoContent()).andReturn();
        var session = (MockHttpSession) loggedIn.getRequest().getSession(false);
        assertNotNull(session);
        mvc.perform(get("/api/auth/me").session(session)).andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id)).andExpect(jsonPath("$.email").value(created.email));
    }

    @Test
    void ambiguousLegacyEmailCaseVariantsCannotAuthenticateAsEitherAccount() throws Exception {
        seed(target.email.toUpperCase(Locale.ROOT), "Tài khoản email trùng", Role.HR);
        for (String email : List.of(target.email, target.email.toUpperCase(Locale.ROOT))) {
            mvc.perform(post("/api/auth/login").with(csrf()).param("username", email).param("password", PASSWORD))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Test
    void duplicateEmailIsCaseInsensitiveForCreationAndEditing() throws Exception {
        target.email = target.email.toUpperCase(Locale.ROOT);
        users.saveAndFlush(target);
        var payload = input();
        payload.put("email", "  " + target.email.toLowerCase(Locale.ROOT) + "  ");
        createAs(administrator, payload).andExpect(status().isConflict()).andExpect(jsonPath("$.fields.email").exists());
        editAs(administrator, administrator.id, payload).andExpect(status().isConflict())
                .andExpect(jsonPath("$.fields.email").exists());
        assertEquals(administrator.email, users.findById(administrator.id).orElseThrow().email);
        payload.put("roleId", target.getRoleId());
        editAs(administrator, target.id, payload).andExpect(status().isOk());
        assertEquals(target.email.toLowerCase(Locale.ROOT), users.findById(target.id).orElseThrow().email);
    }

    @Test
    void rejectsInvalidFieldsForBothCreationAndEditingWithoutChangingStoredCredentials() throws Exception {
        for (var invalid : List.of(Map.entry("fullName", " "), Map.entry("fullName", "A"),
                Map.entry("fullName", "A".repeat(121)), Map.entry("email", "invalid-email"), Map.entry("email", " "),
                Map.entry("email", "a".repeat(180) + "@example.test"), Map.entry("phone", "09123abc78"),
                Map.entry("phone", "091234567"), Map.entry("roleId", "0"), Map.entry("roleId", "8"))) {
            var payload = input();
            payload.put(invalid.getKey(), "roleId".equals(invalid.getKey()) ? Long.parseLong(invalid.getValue()) : invalid.getValue());
            createAs(administrator, payload).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fields." + invalid.getKey()).exists());
            editAs(administrator, target.id, payload).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fields." + invalid.getKey()).exists());
        }
        for (String required : List.of("fullName", "email", "roleId")) {
            var payload = input();
            payload.remove(required);
            createAs(administrator, payload).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fields." + required).exists());
        }
        assertEquals(target.passwordHash, users.findById(target.id).orElseThrow().passwordHash);
        assertEquals(target.fullName, users.findById(target.id).orElseThrow().fullName);
    }

    @Test
    void rejectsARoleWhoseDatabaseCodeDoesNotMatchItsFixedId() throws Exception {
        var role = roles.findById(3L).orElseThrow();
        String originalCode = role.code;
        role.code = "UNSUPPORTED_STUDENT";
        roles.saveAndFlush(role);
        try {
            var payload = input();
            payload.put("roleId", 3);
            createAs(administrator, payload).andExpect(status().isBadRequest()).andExpect(jsonPath("$.fields.roleId").exists());
            editAs(administrator, target.id, payload).andExpect(status().isBadRequest());
            mvc.perform(get("/api/admin/roles").with(user(administrator.email).roles("ADMIN")))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(6));
        } finally {
            role.code = originalCode;
            roles.saveAndFlush(role);
        }
    }

    @Test
    void administratorsCannotDemoteThemselvesAndUnknownAccountsReturn404() throws Exception {
        var payload = input();
        payload.put("email", administrator.email);
        payload.put("roleId", 7);
        editAs(administrator, administrator.id, payload).andExpect(status().isConflict())
                .andExpect(jsonPath("$.fields.roleId").exists());
        assertEquals(Role.ADMIN, users.findWithRoleById(administrator.id).orElseThrow().getRoleCode());
        editAs(administrator, Long.MAX_VALUE, input()).andExpect(status().isNotFound());
    }

    @Test
    void searchesNamesEmailAndPhoneWithoutTreatingPercentUnderscoreOrBackslashAsWildcards() throws Exception {
        target.fullName = "Search " + unique + " 100%_\\a";
        target.phone = "0912345678";
        users.saveAndFlush(target);
        for (String query : List.of("SEARCH " + unique.toUpperCase(Locale.ROOT), target.email.toUpperCase(Locale.ROOT),
                "0912345678", "100%_\\a")) {
            mvc.perform(get(API).param("q", query).with(user(administrator.email).roles("ADMIN")))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.items[?(@.id == " + target.id + ")]").isNotEmpty());
        }
        mvc.perform(get(API).param("q", "%missing_" + unique).with(user(administrator.email).roles("ADMIN")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(0));
    }

    @Test
    void pagesInStableDescendingIdOrderAndFiltersByRole() throws Exception {
        var second = seed("second-" + unique + "@accounts.test", "Page " + unique, Role.STUDENT);
        var third = seed("third-" + unique + "@accounts.test", "Page " + unique, Role.HR);
        target.fullName = "Page " + unique;
        users.saveAndFlush(target);
        mvc.perform(get(API).param("q", "Page " + unique).param("size", "2")
                        .with(user(administrator.email).roles("ADMIN")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(3))
                .andExpect(jsonPath("$.totalPages").value(2)).andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(2)).andExpect(jsonPath("$.items[0].id").value(third.id))
                .andExpect(jsonPath("$.items[1].id").value(second.id))
                .andExpect(jsonPath("$.items[0].passwordHash").doesNotHaveJsonPath());
        mvc.perform(get(API).param("q", "Page " + unique).param("size", "2").param("page", "1")
                        .with(user(administrator.email).roles("ADMIN")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].id").value(target.id));
        mvc.perform(get(API).param("q", "Page " + unique).param("roleId", "3")
                        .with(user(administrator.email).roles("ADMIN")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(2));
        for (var invalid : List.of(Map.entry("page", "-1"), Map.entry("size", "0"),
                Map.entry("size", "101"), Map.entry("roleId", "8"), Map.entry("q", "q".repeat(201)))) {
            mvc.perform(get(API).param(invalid.getKey(), invalid.getValue()).with(user(administrator.email).roles("ADMIN")))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    void accountMutationsRequireCsrf() throws Exception {
        for (var request : List.of(post(API), put(API + "/" + target.id), delete(API + "/" + target.id),
                patch(API + "/" + target.id + "/status"), patch(API + "/" + target.id + "/lock"))) {
            mvc.perform(request.with(user(administrator.email).roles("ADMIN")).contentType("application/json")
                            .content(json.writeValueAsString(input())))
                    .andExpect(status().isForbidden());
        }
        assertEquals(target.passwordHash, users.findById(target.id).orElseThrow().passwordHash);
    }

    @Test
    void creationRequiresAValidPasswordAndMatchingConfirmationWithoutStoringInvalidAccounts() throws Exception {
        for (String required : List.of("password", "confirmPassword")) {
            var payload = input();
            payload.remove(required);
            createAs(administrator, payload).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fields." + required).exists());
        }
        for (String invalid : List.of("", " ".repeat(8), "short", "😀".repeat(7), "a".repeat(73), "á".repeat(37))) {
            var payload = input();
            payload.put("password", invalid);
            payload.put("confirmPassword", invalid);
            createAs(administrator, payload).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fields.password").exists());
            if (!invalid.isBlank()) editAs(administrator, target.id, payload).andExpect(status().isBadRequest());
        }
        var mismatch = input();
        mismatch.put("confirmPassword", "DifferentPassword@2026");
        createAs(administrator, mismatch).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.confirmPassword").exists());
        editAs(administrator, target.id, mismatch).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.confirmPassword").exists());
        assertEquals(target.passwordHash, users.findById(target.id).orElseThrow().passwordHash);
        assertFalse(users.existsByEmailIgnoreCase(input().get("email").toString()));
        // Eight supplementary Unicode characters count as eight characters, not sixteen UTF-16 units.
        var unicode = input();
        unicode.put("email", "unicode-" + unique + "@accounts.test");
        unicode.put("password", "😀".repeat(8));
        unicode.put("confirmPassword", "😀".repeat(8));
        createAs(administrator, unicode).andExpect(status().isCreated());
        login(unicode.get("email").toString(), "😀".repeat(8)).andExpect(status().isNoContent());
    }

    @Test
    void editingRejectsAnIncompletePasswordPairAndKeepsTheStoredPassword() throws Exception {
        for (String omitted : List.of("password", "confirmPassword")) {
            var payload = input();
            payload.remove(omitted);
            editAs(administrator, target.id, payload).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fields." + omitted).exists());
        }
        var noPassword = input();
        noPassword.put("email", target.email);
        noPassword.put("roleId", target.getRoleId());
        noPassword.remove("password");
        noPassword.remove("confirmPassword");
        editAs(administrator, target.id, noPassword).andExpect(status().isOk());
        User saved = users.findById(target.id).orElseThrow();
        assertEquals(target.passwordHash, saved.passwordHash);
        assertEquals(target.credentialsVersion, saved.credentialsVersion);
    }

    @Test
    void editingAPasswordRevokesLoginSessionsAndPreviouslyIssuedResetCodesAndTokens() throws Exception {
        var loggedIn = login(target.email, PASSWORD).andExpect(status().isNoContent()).andReturn();
        var session = (MockHttpSession) loggedIn.getRequest().getSession(false);
        seedRecovery(target, "123456", "ResetTokenForAdminChange");
        var payload = input();
        payload.put("email", target.email);
        payload.put("roleId", target.getRoleId());
        payload.put("password", "NewPassword@2026");
        payload.put("confirmPassword", "NewPassword@2026");
        editAs(administrator, target.id, payload).andExpect(status().isOk())
                .andExpect(jsonPath("$.password").doesNotHaveJsonPath())
                .andExpect(jsonPath("$.confirmPassword").doesNotHaveJsonPath())
                .andExpect(jsonPath("$.passwordHash").doesNotHaveJsonPath());
        User saved = users.findById(target.id).orElseThrow();
        assertTrue(encoder.matches("NewPassword@2026", saved.passwordHash));
        assertEquals(target.credentialsVersion + 1, saved.credentialsVersion);
        login(target.email, PASSWORD).andExpect(status().isUnauthorized());
        login(target.email, "NewPassword@2026").andExpect(status().isNoContent());
        mvc.perform(get("/api/auth/me").session(session)).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("SESSION_EXPIRED"));
        resetPost("/verify", Map.of("email", target.email, "code", "123456"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("RESET_CODE_INVALID"));
        resetPost("/complete", Map.of("email", target.email, "resetToken", "ResetTokenForAdminChange",
                        "newPassword", PASSWORD, "confirmPassword", PASSWORD))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("RESET_TOKEN_INVALID"));
    }

    @Test
    void detailReturnsOnlyPublicAccountInformationAndUnknownAccountsReturn404() throws Exception {
        mvc.perform(get(API + "/" + target.id).with(user(administrator.email).roles("ADMIN")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(target.id))
                .andExpect(jsonPath("$.email").value(target.email))
                .andExpect(jsonPath("$.fullName").value(target.fullName))
                .andExpect(jsonPath("$.roleId").value(3)).andExpect(jsonPath("$.role").value(Role.STUDENT))
                .andExpect(jsonPath("$.accountEnabled").value(true)).andExpect(jsonPath("$.accountLocked").value(false))
                .andExpect(jsonPath("$.password").doesNotHaveJsonPath())
                .andExpect(jsonPath("$.passwordHash").doesNotHaveJsonPath())
                .andExpect(jsonPath("$.credentialsVersion").doesNotHaveJsonPath());
        mvc.perform(get(API + "/" + Long.MAX_VALUE).with(user(administrator.email).roles("ADMIN")))
                .andExpect(status().isNotFound());
    }

    @Test
    void disablingAndReenablingPreservesAccountInformationAndChangesTheCredentialVersionOnlyOnTransitions() throws Exception {
        target.phone = "0912345678";
        users.saveAndFlush(target);
        long initialVersion = target.credentialsVersion;
        statusAs(administrator, target.id, true).andExpect(status().isOk())
                .andExpect(jsonPath("$.accountEnabled").value(true));
        assertEquals(initialVersion, users.findById(target.id).orElseThrow().credentialsVersion);
        statusAs(administrator, target.id, false).andExpect(status().isOk())
                .andExpect(jsonPath("$.accountEnabled").value(false))
                .andExpect(jsonPath("$.accountLocked").value(false))
                .andExpect(jsonPath("$.passwordHash").doesNotHaveJsonPath())
                .andExpect(jsonPath("$.credentialsVersion").doesNotHaveJsonPath());
        statusAs(administrator, target.id, false).andExpect(status().isOk());
        assertEquals(initialVersion + 1, users.findById(target.id).orElseThrow().credentialsVersion);
        statusAs(administrator, target.id, true).andExpect(status().isOk())
                .andExpect(jsonPath("$.accountEnabled").value(true));
        statusAs(administrator, target.id, true).andExpect(status().isOk());
        User saved = users.findWithRoleById(target.id).orElseThrow();
        assertEquals(initialVersion + 2, saved.credentialsVersion);
        assertEquals(target.email, saved.email);
        assertEquals(target.fullName, saved.fullName);
        assertEquals(target.phone, saved.phone);
        assertEquals(target.getRoleId(), saved.getRoleId());
        assertEquals(target.passwordHash, saved.passwordHash);
    }

    @Test
    void disabledAccountsCannotLogInAndOldSessionsRemainRevokedAfterReenabling() throws Exception {
        var first = login(target.email, PASSWORD).andExpect(status().isNoContent()).andReturn();
        var firstSession = (MockHttpSession) first.getRequest().getSession(false);
        var second = login(target.email, PASSWORD).andExpect(status().isNoContent()).andReturn();
        var secondSession = (MockHttpSession) second.getRequest().getSession(false);
        assertNotNull(firstSession);
        assertNotNull(secondSession);
        statusAs(administrator, target.id, false).andExpect(status().isOk());
        login(target.email, PASSWORD).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/auth/me").session(firstSession)).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("SESSION_EXPIRED"));
        assertTrue(firstSession.isInvalid());
        statusAs(administrator, target.id, true).andExpect(status().isOk());
        mvc.perform(get("/api/auth/me").session(secondSession)).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("SESSION_EXPIRED"));
        assertTrue(secondSession.isInvalid());
        var fresh = login(target.email, PASSWORD).andExpect(status().isNoContent()).andReturn();
        mvc.perform(get("/api/auth/me").session((MockHttpSession) fresh.getRequest().getSession(false)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(target.id));
    }

    @Test
    void statusChangesPermanentlyRevokePreviouslyIssuedPasswordResetCodesAndTokens() throws Exception {
        String token = "ResetTokenBeforeDisablingAccount";
        seedRecovery(target, "123456", token);
        statusAs(administrator, target.id, false).andExpect(status().isOk());
        resetPost("/verify", Map.of("email", target.email, "code", "123456"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("RESET_CODE_INVALID"));
        resetPost("/complete", Map.of("email", target.email, "resetToken", token,
                        "newPassword", "AttemptedPassword@2026", "confirmPassword", "AttemptedPassword@2026"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("RESET_TOKEN_INVALID"));
        statusAs(administrator, target.id, true).andExpect(status().isOk());
        resetPost("/verify", Map.of("email", target.email, "code", "123456"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("RESET_CODE_INVALID"));
        resetPost("/complete", Map.of("email", target.email, "resetToken", token,
                        "newPassword", "AttemptedPassword@2026", "confirmPassword", "AttemptedPassword@2026"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("RESET_TOKEN_INVALID"));
        assertEquals(target.passwordHash, users.findById(target.id).orElseThrow().passwordHash);
    }

    @Test
    void reenablingAnAccountDoesNotUnlockItOrAllowLoginWhileLocked() throws Exception {
        target.accountLocked = true;
        users.saveAndFlush(target);
        statusAs(administrator, target.id, false).andExpect(status().isOk())
                .andExpect(jsonPath("$.accountLocked").value(true));
        statusAs(administrator, target.id, true).andExpect(status().isOk())
                .andExpect(jsonPath("$.accountEnabled").value(true))
                .andExpect(jsonPath("$.accountLocked").value(true));
        login(target.email, PASSWORD).andExpect(status().isUnauthorized());
        assertTrue(users.findById(target.id).orElseThrow().accountLocked);
    }

    @Test
    void administratorsCannotDisableTheirOwnAccountAndUnknownStatusTargetsReturn404() throws Exception {
        statusAs(administrator, administrator.id, false).andExpect(status().isConflict())
                .andExpect(jsonPath("$.fields.accountEnabled").exists());
        User saved = users.findById(administrator.id).orElseThrow();
        assertTrue(saved.accountEnabled);
        assertEquals(administrator.credentialsVersion, saved.credentialsVersion);
        statusAs(administrator, administrator.id, true).andExpect(status().isOk());
        statusAs(administrator, Long.MAX_VALUE, false).andExpect(status().isNotFound());
        statusAs(administrator, Long.MAX_VALUE, true).andExpect(status().isNotFound());
    }

    @Test
    void accountStatusRequiresAnExplicitJsonBooleanAndNeverChangesDataForInvalidInput() throws Exception {
        for (String payload : List.of("{}", "{\"accountEnabled\":null}", "{\"accountEnabled\":\"false\"}",
                "{\"accountEnabled\":0}", "{\"accountEnabled\":[]}", "{\"accountEnabled\":{}}")) {
            mvc.perform(patch(API + "/" + target.id + "/status").with(user(administrator.email).roles("ADMIN"))
                            .with(csrf()).contentType("application/json").content(payload))
                    .andExpect(status().isBadRequest());
        }
        User saved = users.findById(target.id).orElseThrow();
        assertTrue(saved.accountEnabled);
        assertEquals(target.credentialsVersion, saved.credentialsVersion);
    }

    @Test
    void anAdministratorCanDisableAndReenableAnotherAdministratorWhileKeepingAnActiveAdministrator() throws Exception {
        target.role = roles.findByCode(Role.ADMIN).orElseThrow();
        users.saveAndFlush(target);
        statusAs(administrator, target.id, false).andExpect(status().isOk());
        assertTrue(users.findById(administrator.id).orElseThrow().accountEnabled);
        assertTrue(users.countUsableAdministrators() >= 1);
        statusAs(administrator, target.id, true).andExpect(status().isOk());
        assertTrue(users.findById(target.id).orElseThrow().accountEnabled);
    }

    @Test
    void lockingAndUnlockingPreservesAccountInformationAndChangesTheCredentialVersionOnlyOnTransitions() throws Exception {
        target.phone = "0912345678";
        users.saveAndFlush(target);
        long initialVersion = target.credentialsVersion;
        lockAs(administrator, target.id, false).andExpect(status().isOk())
                .andExpect(jsonPath("$.accountLocked").value(false));
        assertEquals(initialVersion, users.findById(target.id).orElseThrow().credentialsVersion);
        lockAs(administrator, target.id, true).andExpect(status().isOk())
                .andExpect(jsonPath("$.accountLocked").value(true))
                .andExpect(jsonPath("$.accountEnabled").value(true))
                .andExpect(jsonPath("$.passwordHash").doesNotHaveJsonPath())
                .andExpect(jsonPath("$.credentialsVersion").doesNotHaveJsonPath());
        lockAs(administrator, target.id, true).andExpect(status().isOk());
        assertEquals(initialVersion + 1, users.findById(target.id).orElseThrow().credentialsVersion);
        lockAs(administrator, target.id, false).andExpect(status().isOk())
                .andExpect(jsonPath("$.accountLocked").value(false));
        lockAs(administrator, target.id, false).andExpect(status().isOk());
        User saved = users.findWithRoleById(target.id).orElseThrow();
        assertEquals(initialVersion + 2, saved.credentialsVersion);
        assertEquals(target.email, saved.email);
        assertEquals(target.fullName, saved.fullName);
        assertEquals(target.phone, saved.phone);
        assertEquals(target.getRoleId(), saved.getRoleId());
        assertEquals(target.passwordHash, saved.passwordHash);
        assertTrue(saved.accountEnabled);
    }

    @Test
    void lockedAccountsCannotLogInAndOldSessionsRemainRevokedAfterUnlocking() throws Exception {
        var first = login(target.email, PASSWORD).andExpect(status().isNoContent()).andReturn();
        var firstSession = (MockHttpSession) first.getRequest().getSession(false);
        var second = login(target.email, PASSWORD).andExpect(status().isNoContent()).andReturn();
        var secondSession = (MockHttpSession) second.getRequest().getSession(false);
        assertNotNull(firstSession);
        assertNotNull(secondSession);
        lockAs(administrator, target.id, true).andExpect(status().isOk());
        login(target.email, PASSWORD).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/dashboard/student").session(firstSession)).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("SESSION_EXPIRED"));
        assertTrue(firstSession.isInvalid());
        lockAs(administrator, target.id, false).andExpect(status().isOk());
        mvc.perform(get("/api/auth/me").session(secondSession)).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("SESSION_EXPIRED"));
        assertTrue(secondSession.isInvalid());
        var fresh = login(target.email, PASSWORD).andExpect(status().isNoContent()).andReturn();
        mvc.perform(get("/api/auth/me").session((MockHttpSession) fresh.getRequest().getSession(false)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(target.id));
    }

    @Test
    void lockChangesPermanentlyRevokePreviouslyIssuedPasswordResetCodesAndTokens() throws Exception {
        String token = "ResetTokenBeforeLockingAccount";
        seedRecovery(target, "123456", token);
        lockAs(administrator, target.id, true).andExpect(status().isOk());
        for (boolean locked : List.of(true, false)) {
            if (!locked) lockAs(administrator, target.id, false).andExpect(status().isOk());
            resetPost("/verify", Map.of("email", target.email, "code", "123456"))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("RESET_CODE_INVALID"));
            resetPost("/complete", Map.of("email", target.email, "resetToken", token,
                            "newPassword", "AttemptedPassword@2026", "confirmPassword", "AttemptedPassword@2026"))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("RESET_TOKEN_INVALID"));
        }
        assertEquals(target.passwordHash, users.findById(target.id).orElseThrow().passwordHash);
    }

    @Test
    void unlockingADisabledAccountDoesNotEnableItOrAllowLogin() throws Exception {
        statusAs(administrator, target.id, false).andExpect(status().isOk());
        lockAs(administrator, target.id, true).andExpect(status().isOk())
                .andExpect(jsonPath("$.accountEnabled").value(false));
        lockAs(administrator, target.id, false).andExpect(status().isOk())
                .andExpect(jsonPath("$.accountLocked").value(false))
                .andExpect(jsonPath("$.accountEnabled").value(false));
        login(target.email, PASSWORD).andExpect(status().isUnauthorized());
        assertFalse(users.findById(target.id).orElseThrow().accountEnabled);
    }

    @Test
    void administratorsCannotLockTheirOwnAccountAndUnknownLockTargetsReturn404() throws Exception {
        lockAs(administrator, administrator.id, true).andExpect(status().isConflict())
                .andExpect(jsonPath("$.fields.accountLocked").exists());
        User saved = users.findById(administrator.id).orElseThrow();
        assertFalse(saved.accountLocked);
        assertEquals(administrator.credentialsVersion, saved.credentialsVersion);
        lockAs(administrator, administrator.id, false).andExpect(status().isOk());
        lockAs(administrator, Long.MAX_VALUE, true).andExpect(status().isNotFound());
        lockAs(administrator, Long.MAX_VALUE, false).andExpect(status().isNotFound());
    }

    @Test
    void accountLockRequiresAnExplicitJsonBooleanAndNeverChangesDataForInvalidInput() throws Exception {
        for (String payload : List.of("{}", "{\"accountLocked\":null}", "{\"accountLocked\":\"true\"}",
                "{\"accountLocked\":1}", "{\"accountLocked\":[]}", "{\"accountLocked\":{}}")) {
            mvc.perform(patch(API + "/" + target.id + "/lock").with(user(administrator.email).roles("ADMIN"))
                            .with(csrf()).contentType("application/json").content(payload))
                    .andExpect(status().isBadRequest());
        }
        User saved = users.findById(target.id).orElseThrow();
        assertFalse(saved.accountLocked);
        assertEquals(target.credentialsVersion, saved.credentialsVersion);
    }

    @Test
    void anAdministratorCanLockAndUnlockAnotherAdministratorWhileKeepingAnActiveAdministrator() throws Exception {
        target.role = roles.findByCode(Role.ADMIN).orElseThrow();
        users.saveAndFlush(target);
        lockAs(administrator, target.id, true).andExpect(status().isOk());
        assertFalse(users.findById(administrator.id).orElseThrow().accountLocked);
        assertTrue(users.countUsableAdministrators() >= 1);
        lockAs(administrator, target.id, false).andExpect(status().isOk());
        assertFalse(users.findById(target.id).orElseThrow().accountLocked);
    }

    @Test
    void freshAdminDashboardStatisticsAndTasksReflectEveryDisableEnableLockAndUnlockTransition() throws Exception {
        JsonNode initial = adminDashboard();
        long accounts = dashboardCount(initial, "stats", "accounts");
        long active = dashboardCount(initial, "stats", "enabled");
        long locked = dashboardCount(initial, "stats", "locked");
        long disabled = dashboardCount(initial, "tasks", "disabled");
        statusAs(administrator, target.id, false).andExpect(status().isOk());
        assertDashboardCounts(accounts, active - 1, locked, disabled + 1);
        lockAs(administrator, target.id, true).andExpect(status().isOk());
        assertDashboardCounts(accounts, active - 1, locked + 1, disabled + 1);
        lockAs(administrator, target.id, false).andExpect(status().isOk());
        assertDashboardCounts(accounts, active - 1, locked, disabled + 1);
        statusAs(administrator, target.id, true).andExpect(status().isOk());
        assertDashboardCounts(accounts, active, locked, disabled);
        lockAs(administrator, target.id, true).andExpect(status().isOk());
        assertDashboardCounts(accounts, active - 1, locked + 1, disabled);
        lockAs(administrator, target.id, false).andExpect(status().isOk());
        assertDashboardCounts(accounts, active, locked, disabled);
    }

    @Test
    void administratorsCannotDeleteThemselvesAndDeletingUnknownAccountsReturns404() throws Exception {
        deleteAs(administrator, administrator.id).andExpect(status().isConflict());
        assertTrue(users.existsById(administrator.id));
        deleteAs(administrator, Long.MAX_VALUE).andExpect(status().isNotFound());
    }

    @Test
    void deletingAnUnreferencedAccountCleansAuthenticationDataRevokesSessionsAndReleasesItsEmail() throws Exception {
        var loggedIn = login(target.email, PASSWORD).andExpect(status().isNoContent()).andReturn();
        var session = (MockHttpSession) loggedIn.getRequest().getSession(false);
        assertNotNull(session);
        seedRecovery(target, "123456", "ResetTokenForDeletedAccount");
        assertTrue(jdbc.queryForObject("SELECT COUNT(*) FROM user_login_events WHERE user_id=?", Long.class, target.id) > 0);
        deleteAs(administrator, target.id).andExpect(status().isNoContent());
        assertFalse(users.existsById(target.id));
        assertEquals(0L, jdbc.queryForObject("SELECT COUNT(*) FROM user_login_events WHERE user_id=?", Long.class, target.id));
        assertEquals(0L, jdbc.queryForObject("SELECT COUNT(*) FROM password_reset_challenges WHERE user_id=?", Long.class, target.id));
        login(target.email, PASSWORD).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/auth/me").session(session)).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("SESSION_EXPIRED"));
        resetPost("/verify", Map.of("email", target.email, "code", "123456"))
                .andExpect(status().isBadRequest());
        resetPost("/complete", Map.of("email", target.email, "resetToken", "ResetTokenForDeletedAccount",
                        "newPassword", PASSWORD, "confirmPassword", PASSWORD))
                .andExpect(status().isBadRequest());
        var reused = input();
        reused.put("email", target.email);
        createAs(administrator, reused).andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value(target.email));
        assertNotEquals(target.id, users.findByEmail(target.email).orElseThrow().id);
    }

    @Test
    void everyBusinessReferencePreventsDeletionAndPreservesTheAccountAndRelatedRow() throws Exception {
        UUID classId = UUID.randomUUID(), sessionId = UUID.randomUUID(), assetId = UUID.randomUUID();
        UUID requestId = UUID.randomUUID(), consultationId = UUID.randomUUID();
        jdbc.update("INSERT INTO training_classes(id,code,name,lecturer_id,status) VALUES (?,?,?,?,'ACTIVE')",
                classId, unique, "Lớp kiểm tra xóa", administrator.id);
        jdbc.update("INSERT INTO training_sessions(id,class_id,title,starts_at,ends_at) VALUES (?,?,?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP + INTERVAL '1' HOUR)",
                sessionId, classId, "Buổi học");
        jdbc.update("INSERT INTO equipment_assets(id,code,name,status) VALUES (?,?,?,'AVAILABLE')", assetId, unique, "Thiết bị");
        jdbc.update("""
                INSERT INTO recruitment_requests(id,created_by,title,quantity,shift,skills,location,needed_by,status,created_at,updated_at)
                VALUES (?,?,'Yêu cầu',1,'DAY','Kỹ năng','Địa điểm',CURRENT_DATE,'DRAFT',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
                """, requestId, administrator.id);
        jdbc.update("""
                INSERT INTO consultation_registrations(id,full_name,phone,contact_time,program_name,created_date)
                VALUES (?,'Người đăng ký','0912345678','any','Điện công nghiệp',CURRENT_TIMESTAMP)
                """, consultationId);
        var references = List.of(
                new RelatedRow("recruitment_requests", """
                        INSERT INTO recruitment_requests(id,created_by,title,quantity,shift,skills,location,needed_by,status,created_at,updated_at)
                        VALUES (?,?,'Yêu cầu',1,'DAY','Kỹ năng','Địa điểm',CURRENT_DATE,'DRAFT',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
                        """, new Object[]{target.id}),
                new RelatedRow("request_events", "INSERT INTO request_events(id,actor_id,request_id,action,occurred_at) VALUES (?,?,?,'CREATED',CURRENT_TIMESTAMP)", new Object[]{target.id, requestId}),
                new RelatedRow("training_classes", "INSERT INTO training_classes(id,lecturer_id,code,name,status) VALUES (?,?,?,'Lớp giảng dạy','ACTIVE')", new Object[]{target.id, "class-" + unique.substring(0, 20)}),
                new RelatedRow("training_enrollments", "INSERT INTO training_enrollments(id,student_id,class_id,status) VALUES (?,?,?,'ACTIVE')", new Object[]{target.id, classId}),
                new RelatedRow("training_attendance", "INSERT INTO training_attendance(id,student_id,session_id,status) VALUES (?,?,?,'PRESENT')", new Object[]{target.id, sessionId}),
                new RelatedRow("training_contracts", "INSERT INTO training_contracts(id,student_id,code,status) VALUES (?,?,?,'ACTIVE')", new Object[]{target.id, "contract-" + unique.substring(0, 20)}),
                new RelatedRow("equipment_requests", "INSERT INTO equipment_requests(id,requested_by,title,quantity,status) VALUES (?,?,'Yêu cầu thiết bị',1,'NEW')", new Object[]{target.id}),
                new RelatedRow("equipment_loans", "INSERT INTO equipment_loans(id,student_id,asset_id) VALUES (?,?,?)", new Object[]{target.id, assetId}),
                new RelatedRow("equipment_incidents", "INSERT INTO equipment_incidents(id,reported_by,asset_id,description,status) VALUES (?,?,?,'Sự cố','OPEN')", new Object[]{target.id, assetId}),
                new RelatedRow("consultation_followups", "INSERT INTO consultation_followups(id,assigned_to,consultation_id) VALUES (?,?,?)", new Object[]{target.id, consultationId}));
        for (RelatedRow row : references) {
            UUID id = UUID.randomUUID();
            var values = new java.util.ArrayList<Object>();
            values.add(id);
            values.addAll(List.of(row.arguments));
            jdbc.update(row.insert, values.toArray());
            try {
                deleteAs(administrator, target.id).andExpect(status().isConflict())
                        .andExpect(jsonPath("$.message", org.hamcrest.Matchers.containsString("tài khoản đang có dữ liệu liên quan")));
                assertEquals(1L, jdbc.queryForObject("SELECT COUNT(*) FROM " + row.table + " WHERE id=?", Long.class, id));
                assertEquals(target.passwordHash, users.findById(target.id).orElseThrow().passwordHash);
            } finally {
                jdbc.update("DELETE FROM " + row.table + " WHERE id=?", id);
            }
        }
    }

    private record RelatedRow(String table, String insert, Object[] arguments) {}

    private void seedRecovery(User account, String code, String token) throws Exception {
        UUID challengeId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO password_reset_challenges(email_key,user_id,challenge_id,code_hash,code_expires_at,
                    token_hash,token_expires_at,password_snapshot,credentials_version)
                VALUES (?,?,?,?,?,?,?,?,?)
                """, hash(account.email), account.id, challengeId, encoder.encode(code + challengeId),
                Timestamp.from(Instant.now().plusSeconds(600)), hash(token), Timestamp.from(Instant.now().plusSeconds(300)),
                account.passwordHash, account.credentialsVersion);
    }

    private static String hash(String value) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    }

    private ResultActions resetPost(String path, Map<String, Object> payload) throws Exception {
        return mvc.perform(post("/api/auth/password-reset" + path).with(csrf()).contentType("application/json")
                .content(json.writeValueAsString(payload)));
    }

    private User seed(String email, String fullName, String code) {
        var account = new User();
        account.email = email;
        account.fullName = fullName;
        account.passwordHash = encoder.encode(PASSWORD);
        account.role = roles.findByCode(code).orElseThrow();
        return users.saveAndFlush(account);
    }

    private Map<String, Object> input() {
        return new HashMap<>(Map.of("fullName", "Updated Account", "email", "created-" + unique + "@accounts.test",
                "phone", "0912345678", "roleId", 1, "password", PASSWORD, "confirmPassword", PASSWORD));
    }

    private ResultActions createAs(User actor, Map<String, Object> input) throws Exception {
        return mvc.perform(post(API).with(user(actor.email).roles("ADMIN")).with(csrf())
                .contentType("application/json").content(json.writeValueAsString(input)));
    }

    private ResultActions editAs(User actor, Long id, Map<String, Object> input) throws Exception {
        return mvc.perform(put(API + "/" + id).with(user(actor.email).roles("ADMIN")).with(csrf())
                .contentType("application/json").content(json.writeValueAsString(input)));
    }

    private ResultActions deleteAs(User actor, Long id) throws Exception {
        return mvc.perform(delete(API + "/" + id).with(user(actor.email).roles("ADMIN")).with(csrf()));
    }

    private ResultActions statusAs(User actor, Long id, boolean enabled) throws Exception {
        return mvc.perform(patch(API + "/" + id + "/status").with(user(actor.email).roles("ADMIN")).with(csrf())
                .contentType("application/json").content(json.writeValueAsString(Map.of("accountEnabled", enabled))));
    }

    private ResultActions lockAs(User actor, Long id, boolean locked) throws Exception {
        return mvc.perform(patch(API + "/" + id + "/lock").with(user(actor.email).roles("ADMIN")).with(csrf())
                .contentType("application/json").content(json.writeValueAsString(Map.of("accountLocked", locked))));
    }

    private JsonNode adminDashboard() throws Exception {
        return body(mvc.perform(get("/api/dashboard/admin").with(user(administrator.email).roles("ADMIN")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private void assertDashboardCounts(long accounts, long active, long locked, long disabled) throws Exception {
        JsonNode dashboard = adminDashboard();
        assertEquals(accounts, dashboardCount(dashboard, "stats", "accounts"));
        assertEquals(active, dashboardCount(dashboard, "stats", "enabled"));
        assertEquals(locked, dashboardCount(dashboard, "stats", "locked"));
        assertEquals(disabled, dashboardCount(dashboard, "highlights", "disabled"));
        assertEquals(disabled, dashboardCount(dashboard, "tasks", "disabled"));
        assertEquals(locked, dashboardCount(dashboard, "tasks", "locked"));
    }

    private static long dashboardCount(JsonNode dashboard, String section, String key) {
        for (JsonNode item : dashboard.path(section)) {
            if (key.equals(item.path("key").asText())) return item.path(section.equals("tasks") ? "count" : "value").asLong();
        }
        throw new AssertionError("Missing dashboard count: " + section + "." + key);
    }

    private ResultActions login(String email, String password) throws Exception {
        return mvc.perform(post("/api/auth/login").with(csrf()).param("username", email).param("password", password));
    }

    private JsonNode body(String value) throws Exception { return json.readTree(value); }
}

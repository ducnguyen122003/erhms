package com.erhms;

import com.erhms.recruitment.repository.RecruitmentRepository;
import com.erhms.recruitment.repository.RequestEventRepository;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RecruitmentIntegrationTest {
    private static final String OWNER = "owner@test.local";
    private static final String MANAGER = "manager@test.local";
    private static final String OTHER = "other@test.local";
    private static final String PASSWORD = "TestPassword@2026";
    private static final String REQUESTS = "/api/recruitment-requests";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserRepository users;
    @Autowired RoleRepository roles;
    @Autowired RecruitmentRepository requests;
    @Autowired RequestEventRepository events;
    @Autowired PasswordEncoder encoder;

    @BeforeEach
    void setup() {
        events.deleteAll();
        requests.deleteAll();
        users.deleteAll();
        seedUser(OWNER, Role.USER);
        seedUser(OTHER, Role.USER);
        seedUser(MANAGER, Role.SERVICE_MANAGER);
    }

    @Test
    void anonymousCannotRead() throws Exception {
        mvc.perform(get(REQUESTS)).andExpect(status().isUnauthorized());
    }

    @Test
    void csrfRequired() throws Exception {
        mvc.perform(post(REQUESTS)
                        .with(user(OWNER).roles(Role.USER))
                        .contentType("application/json")
                        .content(json.writeValueAsString(input())))
                .andExpect(status().isForbidden());
    }

    @Test
    void invalidQuantityRejected() throws Exception {
        var data = input();
        data.put("quantity", 0);
        mvc.perform(post(REQUESTS)
                        .with(user(OWNER).roles(Role.USER)).with(csrf())
                        .contentType("application/json").content(json.writeValueAsString(data)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.quantity").exists());
    }

    @Test
    void pastDateRejected() throws Exception {
        var data = input();
        data.put("neededBy", "2000-01-01");
        mvc.perform(post(REQUESTS)
                        .with(user(OWNER).roles(Role.USER)).with(csrf())
                        .contentType("application/json").content(json.writeValueAsString(data)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void cannotReadAnotherUsersRequestsOrHistory() throws Exception {
        String path = requestPath(create());
        mvc.perform(get(path).with(user(OTHER).roles(Role.USER)))
                .andExpect(status().isNotFound());
        mvc.perform(get(path + "/history").with(user(OTHER).roles(Role.USER)))
                .andExpect(status().isNotFound());
        mvc.perform(get(REQUESTS).with(user(OTHER).roles(Role.USER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0));
    }

    @Test
    void userCannotApprove() throws Exception {
        var request = create();
        mvc.perform(post(requestPath(request) + "/approve")
                        .with(user(OWNER).roles(Role.USER)).with(csrf())
                        .contentType("application/json").content(commandBody(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    void cannotEditOrSubmitAnotherUsersRequest() throws Exception {
        var request = create();
        String path = requestPath(request);
        mvc.perform(put(path)
                        .with(user(OTHER).roles(Role.USER)).with(csrf())
                        .contentType("application/json").content(json.writeValueAsString(input())))
                .andExpect(status().isNotFound());
        mvc.perform(post(path + "/submit")
                        .with(user(OTHER).roles(Role.USER)).with(csrf())
                        .contentType("application/json").content(commandBody(request)))
                .andExpect(status().isNotFound());
        mvc.perform(get(path).with(user(OWNER).roles(Role.USER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.version").value(request.get("version").asLong()));
        assertEquals(1, events.count());
    }

    @Test
    void staleEditRejected() throws Exception {
        String path = requestPath(create());
        var data = input();
        data.put("title", "Updated title");
        mvc.perform(put(path)
                        .with(user(OWNER).roles(Role.USER)).with(csrf())
                        .contentType("application/json").content(json.writeValueAsString(data)))
                .andExpect(status().isOk());
        mvc.perform(put(path)
                        .with(user(OWNER).roles(Role.USER)).with(csrf())
                        .contentType("application/json").content(json.writeValueAsString(data)))
                .andExpect(status().isConflict());
    }

    @Test
    void fullApprovalLifecycle() throws Exception {
        var request = create();
        String path = requestPath(request);
        mvc.perform(post(path + "/approve")
                        .with(user(MANAGER).roles(Role.SERVICE_MANAGER)).with(csrf())
                        .contentType("application/json").content(commandBody(request)))
                .andExpect(status().isConflict());

        request = json.readTree(mvc.perform(post(path + "/submit")
                        .with(user(OWNER).roles(Role.USER)).with(csrf())
                        .contentType("application/json").content(commandBody(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUBMITTED"))
                .andReturn().getResponse().getContentAsString());
        mvc.perform(post(path + "/request-revision")
                        .with(user(MANAGER).roles(Role.SERVICE_MANAGER)).with(csrf())
                        .contentType("application/json").content(commandBody(request)))
                .andExpect(status().isBadRequest());
        request = json.readTree(mvc.perform(post(path + "/request-revision")
                        .with(user(MANAGER).roles(Role.SERVICE_MANAGER)).with(csrf())
                        .contentType("application/json")
                        .content(json.writeValueAsString(Map.of(
                                "version", request.get("version").asLong(),
                                "note", "Bổ sung kỹ năng"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REVISION_REQUIRED"))
                .andReturn().getResponse().getContentAsString());
        request = json.readTree(mvc.perform(post(path + "/submit")
                        .with(user(OWNER).roles(Role.USER)).with(csrf())
                        .contentType("application/json").content(commandBody(request)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        request = json.readTree(mvc.perform(post(path + "/approve")
                        .with(user(MANAGER).roles(Role.SERVICE_MANAGER)).with(csrf())
                        .contentType("application/json").content(commandBody(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andReturn().getResponse().getContentAsString());

        mvc.perform(put(path)
                        .with(user(OWNER).roles(Role.USER)).with(csrf())
                        .contentType("application/json").content(json.writeValueAsString(input())))
                .andExpect(status().isConflict());
        mvc.perform(post(path + "/close")
                        .with(user(MANAGER).roles(Role.SERVICE_MANAGER)).with(csrf())
                        .contentType("application/json").content(commandBody(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSED"));
        mvc.perform(get(path + "/history").with(user(OWNER).roles(Role.USER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(6));
    }

    @Test
    void realLoginReturnsNumericUserAndRoleIdsWithoutEmployerFields() throws Exception {
        var session = login(OWNER);
        var owner = users.findByEmail(OWNER).orElseThrow();
        mvc.perform(get("/api/auth/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.id").value(owner.id))
                .andExpect(jsonPath("$.email").value(OWNER))
                .andExpect(jsonPath("$.roleId").isNumber())
                .andExpect(jsonPath("$.roleId").value(owner.getRoleId()))
                .andExpect(jsonPath("$.role").value(Role.USER))
                .andExpect(jsonPath("$.employerId").doesNotHaveJsonPath());

        var request = create(session);
        assertEquals(owner.id.longValue(), request.get("userId").asLong());
        assertEquals(owner.fullName, request.get("userName").asText());
        assertFalse(request.has("employerId"));
        assertFalse(request.has("employerName"));
    }

    @Test
    void usersShareOneRoleWithoutSharingRequestOwnership() {
        var owner = users.findByEmail(OWNER).orElseThrow();
        var other = users.findByEmail(OTHER).orElseThrow();
        var manager = users.findByEmail(MANAGER).orElseThrow();
        assertEquals(roles.findByCode(Role.USER).orElseThrow().id, owner.getRoleId());
        assertEquals(owner.getRoleId(), other.getRoleId());
        assertNotEquals(owner.id, other.id);
        assertNotEquals(owner.getRoleId(), manager.getRoleId());
    }

    @Test
    void jpaGeneratesIncreasingRoleAndUserIds() {
        long previousRoleId = roles.findAll().stream().mapToLong(Role::getId).max().orElse(0);
        long previousUserId = users.findAll().stream().mapToLong(user -> user.id).max().orElse(0);
        var role = roles.saveAndFlush(new Role("GENERATED_TEST_ROLE", "Generated test role"));
        try {
            assertNotNull(role.id);
            assertTrue(role.id > previousRoleId);
            var first = seedUser("generated-one@test.local", role.code);
            var second = seedUser("generated-two@test.local", role.code);
            assertNotNull(first.id);
            assertNotNull(second.id);
            assertTrue(first.id > previousUserId);
            assertEquals(first.id + 1, second.id);
            assertEquals(role.id, users.findByEmail(first.email).orElseThrow().getRoleId());
            assertEquals(role.id, users.findByEmail(second.email).orElseThrow().getRoleId());
        } finally {
            users.findByEmail("generated-one@test.local").ifPresent(users::delete);
            users.findByEmail("generated-two@test.local").ifPresent(users::delete);
            roles.delete(role);
        }
    }

    @Test
    void realSessionsUseDatabaseRolesAndScopeRequestsToTheirOwners() throws Exception {
        var ownerSession = login(OWNER);
        var otherSession = login(OTHER);
        var managerSession = login(MANAGER);
        var ownerRequest = create(ownerSession);
        var otherRequest = create(otherSession);
        String path = requestPath(ownerRequest);

        mvc.perform(get("/api/auth/me").session(managerSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.roleId").isNumber())
                .andExpect(jsonPath("$.role").value(Role.SERVICE_MANAGER))
                .andExpect(jsonPath("$.employerId").doesNotHaveJsonPath());
        mvc.perform(get(REQUESTS).session(ownerSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].id").value(ownerRequest.get("id").asText()));
        mvc.perform(get(REQUESTS).session(otherSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].id").value(otherRequest.get("id").asText()));
        mvc.perform(get(path).session(otherSession)).andExpect(status().isNotFound());
        mvc.perform(get(REQUESTS).session(managerSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(2));
        mvc.perform(get(path).session(managerSession)).andExpect(status().isOk());
        mvc.perform(post(REQUESTS).session(managerSession).with(csrf())
                        .contentType("application/json").content(json.writeValueAsString(input())))
                .andExpect(status().isForbidden());

        var submitted = json.readTree(mvc.perform(post(path + "/submit")
                        .session(ownerSession).with(csrf())
                        .contentType("application/json").content(commandBody(ownerRequest)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        mvc.perform(post(path + "/approve").session(ownerSession).with(csrf())
                        .contentType("application/json").content(commandBody(submitted)))
                .andExpect(status().isForbidden());
        mvc.perform(post(path + "/approve").session(managerSession).with(csrf())
                        .contentType("application/json").content(commandBody(submitted)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));
    }

    @Test
    void newUserCreatesAndReadsOwnRequestsWithoutEmployerData() throws Exception {
        String otherUsersPath = requestPath(create());
        var newUser = seedUser("new-user@test.local", Role.USER);
        var session = login(newUser.email);
        mvc.perform(get("/api/auth/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value(Role.USER))
                .andExpect(jsonPath("$.employerId").doesNotHaveJsonPath());
        mvc.perform(get(REQUESTS).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0));

        var request = create(session);
        mvc.perform(get(requestPath(request)).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(newUser.id))
                .andExpect(jsonPath("$.userName").value(newUser.fullName));
        mvc.perform(get(requestPath(request) + "/history").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
        mvc.perform(get(otherUsersPath).session(session)).andExpect(status().isNotFound());
        mvc.perform(get(otherUsersPath + "/history").session(session)).andExpect(status().isNotFound());
        assertEquals(2, requests.count());
    }

    @Test
    void unsupportedRoleCannotAccessRecruitmentRequests() throws Exception {
        var request = create();
        String path = requestPath(request);
        var role = roles.saveAndFlush(new Role("READ_ONLY_TEST_ROLE", "Unsupported test role"));
        try {
            var account = seedUser("read-only@test.local", role.code);
            var session = login(account.email);
            mvc.perform(get("/api/auth/me").session(session))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.role").value(role.code));
            mvc.perform(get(REQUESTS).session(session)).andExpect(status().isForbidden());
            mvc.perform(get(path).session(session)).andExpect(status().isForbidden());
            mvc.perform(get(path + "/history").session(session)).andExpect(status().isForbidden());
            mvc.perform(post(REQUESTS).session(session).with(csrf())
                            .contentType("application/json").content(json.writeValueAsString(input())))
                    .andExpect(status().isForbidden());
            mvc.perform(put(path).session(session).with(csrf())
                            .contentType("application/json").content(json.writeValueAsString(input())))
                    .andExpect(status().isForbidden());
            mvc.perform(post(path + "/submit").session(session).with(csrf())
                            .contentType("application/json").content(commandBody(request)))
                    .andExpect(status().isForbidden());
            mvc.perform(post(path + "/approve").session(session).with(csrf())
                            .contentType("application/json").content(commandBody(request)))
                    .andExpect(status().isForbidden());
            assertEquals(1, requests.count());
            assertEquals(1, events.count());
        } finally {
            users.findByEmail("read-only@test.local").ifPresent(users::delete);
            roles.delete(role);
        }
    }

    private User seedUser(String email, String roleCode) {
        var user = new User();
        user.email = email;
        user.fullName = email;
        user.role = roles.findByCode(roleCode).orElseThrow();
        user.passwordHash = encoder.encode(PASSWORD);
        return users.saveAndFlush(user);
    }

    private Map<String, Object> input() {
        return new HashMap<>(Map.of(
                "title", "Công nhân vận hành", "quantity", 12, "shift", "DAY",
                "skills", "Vận hành máy cơ bản", "location", "Lô A1",
                "neededBy", LocalDate.now().plusDays(10).toString(), "version", 0));
    }

    private JsonNode create() throws Exception {
        return json.readTree(mvc.perform(post(REQUESTS)
                        .with(user(OWNER).roles(Role.USER)).with(csrf())
                        .contentType("application/json").content(json.writeValueAsString(input())))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
    }

    private JsonNode create(MockHttpSession session) throws Exception {
        return json.readTree(mvc.perform(post(REQUESTS).session(session).with(csrf())
                        .contentType("application/json").content(json.writeValueAsString(input())))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
    }

    private MockHttpSession login(String email) throws Exception {
        var result = mvc.perform(post("/api/auth/login").with(csrf())
                        .param("username", email).param("password", PASSWORD))
                .andExpect(status().isNoContent()).andReturn();
        var session = (MockHttpSession) result.getRequest().getSession(false);
        assertNotNull(session);
        return session;
    }

    private String commandBody(JsonNode request) throws Exception {
        return json.writeValueAsString(Map.of("version", request.get("version").asLong()));
    }

    private String requestPath(JsonNode request) {
        return REQUESTS + "/" + request.get("id").asText();
    }
}

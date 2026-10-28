package com.erhms;

import com.erhms.role.entity.Role;
import com.erhms.role.repository.RoleRepository;
import com.erhms.user.entity.User;
import com.erhms.user.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:erhms_dashboard_test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class DashboardIntegrationTest {
    private static final String PASSWORD = "DashboardTest@2026";
    private static final String INVALID_ROLE_MESSAGE =
            "Vai trò không hợp lệ. Tài khoản phải có role_id từ 1 đến 7 và mã role tương ứng.";
    private static final List<String> ROLES = List.of(
            "ADMIN", "LECTURER", "STUDENT", "ACCOUNTANT", "IT_SUPPORT", "MANAGER", "HR");

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserRepository users;
    @Autowired RoleRepository roles;
    @Autowired PasswordEncoder encoder;
    @Autowired JdbcTemplate jdbc;

    @ParameterizedTest
    @CsvSource({"ADMIN,admin", "LECTURER,lecturer", "STUDENT,student", "ACCOUNTANT,accountant",
            "IT_SUPPORT,it-support", "MANAGER,manager", "HR,hr"})
    void loginReturnsOnlyTheUsersOwnDashboard(String role, String slug) throws Exception {
        User account = account(role);
        assertEquals(ROLES.indexOf(role) + 1L, account.getRoleId());
        MockHttpSession session = login(account.email);
        JsonNode profile = json.readTree(mvc.perform(get("/api/auth/me").session(session))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertEquals(account.getRoleId().longValue(), profile.path("roleId").asLong());
        assertEquals(role, profile.path("role").asText());
        JsonNode dashboard = dashboard(session, "/api/dashboard");
        assertEquals(role, dashboard.path("role").asText());
        assertContract(dashboard);
        assertEquals(role, dashboard(session, "/api/dashboard/" + slug).path("role").asText());
        for (String other : ROLES) {
            if (!role.equals(other)) {
                mvc.perform(get("/api/dashboard/" + slug(other)).session(session))
                        .andExpect(status().isForbidden());
            }
        }
    }

    @ParameterizedTest
    @CsvSource({"Lecture", "LECTURE", "TEACHER", "USER", "EMPLOYER_HR", "SERVICE_MANAGER",
            "lecturer", "ROLE_LECTURER"})
    void legacyAndNonCanonicalRoleCodesAreRejectedEvenAtAValidId(String code) throws Exception {
        User account = account("LECTURER");
        account.role.code = code;
        roles.saveAndFlush(account.role);
        assertEquals(2L, account.getRoleId());
        assertRejectedRoleLogin(account);
        mvc.perform(get("/api/dashboard").with(user(account.email).roles("LECTURER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void anonymousCannotReadAnyDashboard() throws Exception {
        mvc.perform(get("/api/dashboard")).andExpect(status().isUnauthorized());
        for (String role : ROLES) {
            mvc.perform(get("/api/dashboard/" + slug(role))).andExpect(status().isUnauthorized());
        }
    }

    @Test
    void unsupportedDatabaseRoleCannotReadDashboard() throws Exception {
        User account = account("DASHBOARD_UNSUPPORTED_TEST");
        assertTrue(account.getRoleId() > 7);
        assertRejectedRoleLogin(account);
        mvc.perform(get("/api/dashboard").with(user(account.email).roles("ADMIN")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/dashboard/admin").with(user(account.email).roles("ADMIN")))
                .andExpect(status().isForbidden());
    }

    @Test
    void supportedCodeWithIdOutsideTheSevenRolesCannotLoginOrReadDashboard() throws Exception {
        Role canonicalAdmin = roles.findByCode("ADMIN").orElseThrow();
        canonicalAdmin.code = "ADMIN_RENAMED_TEST";
        roles.saveAndFlush(canonicalAdmin);
        User account = account("ADMIN");
        assertTrue(account.getRoleId() > 7);
        assertRejectedRoleLogin(account);
        mvc.perform(get("/api/dashboard/admin").with(user(account.email).roles("ADMIN")))
                .andExpect(status().isForbidden());
    }

    @Test
    void supportedCodeAtTheWrongSupportedIdIsRejected() throws Exception {
        Role hr = roles.findByCode("HR").orElseThrow();
        hr.code = "HR_RENAMED_TEST";
        roles.saveAndFlush(hr);
        User account = account("ADMIN");
        account.role.code = "HR";
        roles.saveAndFlush(account.role);
        assertEquals(1L, account.getRoleId());
        assertRejectedRoleLogin(account);
    }

    @Test
    void invalidRoleDoesNotRevealRoleErrorBeforePasswordVerification() throws Exception {
        User account = account("DASHBOARD_UNSUPPORTED_TEST");
        var result = mvc.perform(post("/api/auth/login").with(csrf())
                        .param("username", account.email).param("password", "IncorrectPassword"))
                .andExpect(status().isUnauthorized()).andReturn();
        assertEquals("Email hoặc mật khẩu không đúng",
                json.readTree(result.getResponse().getContentAsString()).path("message").asText());
        assertRejectedRoleLogin(account);
    }

    @Test
    void existingSessionCannotBeUsedAfterTheDatabaseRoleBecomesInvalid() throws Exception {
        User account = account("HR");
        MockHttpSession session = login(account.email);
        account.role = roles.saveAndFlush(new Role("UNKNOWN_SESSION_ROLE", "Invalid role"));
        users.saveAndFlush(account);
        for (String endpoint : List.of("/api/dashboard", "/api/dashboard/hr", "/api/auth/me",
                "/api/recruitment-requests")) {
            mvc.perform(get(endpoint).session(session)).andExpect(status().isForbidden());
        }
        assertRejectedRoleLogin(account);
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM user_login_events WHERE user_id = ?", Long.class, account.id));
    }

    @Test
    void existingSessionCannotBeUsedAfterTheDatabaseRoleCodeChanges() throws Exception {
        User account = account("STUDENT");
        MockHttpSession session = login(account.email);
        account.role.code = "student";
        roles.saveAndFlush(account.role);
        mvc.perform(get("/api/dashboard/student").session(session)).andExpect(status().isForbidden());
        mvc.perform(get("/api/auth/me").session(session)).andExpect(status().isForbidden());
    }

    @Test
    void dashboardUsesDatabaseRoleInsteadOfClientAuthorities() throws Exception {
        User account = account("STUDENT");
        var result = mvc.perform(get("/api/dashboard").with(user(account.email).roles("ADMIN")))
                .andExpect(status().isOk()).andReturn();
        assertEquals("STUDENT", json.readTree(result.getResponse().getContentAsString()).path("role").asText());
        mvc.perform(get("/api/dashboard/admin").with(user(account.email).roles("ADMIN")))
                .andExpect(status().isForbidden());
    }

    @ParameterizedTest
    @CsvSource({"false,false", "true,true"})
    void disabledOrLockedAccountsCannotLoginOrReuseAnExistingDashboardSession(boolean enabled, boolean locked) throws Exception {
        User account = account("STUDENT");
        MockHttpSession session = login(account.email);
        dashboard(session, "/api/dashboard");
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM user_login_events WHERE user_id = ?", Long.class, account.id));

        account.accountEnabled = enabled;
        account.accountLocked = locked;
        users.saveAndFlush(account);

        mvc.perform(get("/api/dashboard").session(session)).andExpect(status().isForbidden());
        mvc.perform(get("/api/dashboard/student").session(session)).andExpect(status().isForbidden());
        mvc.perform(post("/api/auth/login").with(csrf())
                        .param("username", account.email).param("password", PASSWORD))
                .andExpect(status().isUnauthorized());
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM user_login_events WHERE user_id = ?", Long.class, account.id),
                "Rejected login attempts must not create successful login events");
    }

    @Test
    void lecturerAndStudentMetricsAndRowsStayWithinTheirOwnAssignments() throws Exception {
        User lecturer = account("LECTURER");
        User otherLecturer = account("LECTURER", "other");
        User student = account("STUDENT");
        User otherStudent = account("STUDENT", "other");
        UUID ownClass = trainingClass("OWN-CLASS", lecturer);
        UUID otherClass = trainingClass("OTHER-CLASS", otherLecturer);
        UUID ownEnrollment = enrollment(ownClass, student);
        UUID otherEnrollment = enrollment(otherClass, otherStudent);
        OffsetDateTime now = OffsetDateTime.now();
        UUID ownPastSession = session(ownClass, "Own past lesson", now.minusDays(1));
        UUID otherPastSession = session(otherClass, "Other past lesson", now.minusDays(1));
        session(ownClass, "Own upcoming lesson", now.plusDays(1));
        session(otherClass, "Other upcoming lesson", now.plusDays(1));
        insert("training_attendance", "session_id", ownPastSession, "student_id", student.id, "status", "PRESENT");
        insert("training_attendance", "session_id", otherPastSession, "student_id", otherStudent.id, "status", "ABSENT");
        insert("training_grades", "enrollment_id", ownEnrollment, "title", "Own published grade", "score", 8,
                "published_at", now.minusHours(1));
        insert("training_grades", "enrollment_id", otherEnrollment, "title", "Other published grade", "score", 2,
                "published_at", now.minusHours(1));
        insert("training_grades", "enrollment_id", ownEnrollment, "title", "Unpublished grade", "score", 1);
        UUID ownContract = contract("OWN-CONTRACT", student, "ACTIVE", 1_000_000, 200_000);
        contract("OTHER-CONTRACT", otherStudent, "ACTIVE", 3_000_000, 400_000);
        transaction(ownContract, "TUITION", 600_000, "CONFIRMED");
        request(lecturer, ownClass, "Own equipment request");
        request(otherLecturer, otherClass, "Other equipment request one");
        request(otherLecturer, otherClass, "Other equipment request two");
        UUID ownAsset = insert("equipment_assets", "code", "OWN-ASSET", "name", "Own equipment", "status", "ASSIGNED");
        UUID otherAsset = insert("equipment_assets", "code", "OTHER-ASSET", "name", "Other equipment", "status", "ASSIGNED");
        insert("equipment_loans", "asset_id", ownAsset, "student_id", student.id, "class_id", ownClass,
                "received_at", now.minusDays(2), "return_status", "ACTIVE");
        insert("equipment_loans", "asset_id", otherAsset, "student_id", otherStudent.id, "class_id", otherClass,
                "received_at", now.minusDays(2), "return_status", "ACTIVE");

        JsonNode lecturerDashboard = dashboard(login(lecturer.email), "/api/dashboard");
        assertStat(lecturerDashboard, "classes", 1);
        assertStat(lecturerDashboard, "students", 1);
        assertStat(lecturerDashboard, "requests", 1);
        assertTrue(lecturerDashboard.path("panels").toString().contains("OWN-CLASS"));
        assertFalse(lecturerDashboard.path("panels").toString().contains("OTHER-CLASS"));
        assertFalse(lecturerDashboard.path("panels").toString().contains("Other upcoming lesson"));

        JsonNode studentDashboard = dashboard(login(student.email), "/api/dashboard");
        assertStat(studentDashboard, "attendance", 100);
        assertStat(studentDashboard, "grade", 8);
        assertStat(studentDashboard, "tuition", 400_000);
        assertStat(studentDashboard, "equipment", 1);
        String studentPanels = studentDashboard.path("panels").toString();
        assertTrue(studentPanels.contains("Own upcoming lesson"));
        assertTrue(studentPanels.contains("Own published grade"));
        assertTrue(studentPanels.contains("Own equipment"));
        assertFalse(studentPanels.contains("Other upcoming lesson"));
        assertFalse(studentPanels.contains("Other published grade"));
        assertFalse(studentPanels.contains("Other equipment"));
        assertFalse(studentPanels.contains("Unpublished grade"));

        JsonNode otherDashboard = dashboard(login(otherStudent.email), "/api/dashboard");
        assertStat(otherDashboard, "attendance", 0);
        assertStat(otherDashboard, "grade", 2);
        assertStat(otherDashboard, "tuition", 3_000_000);
    }

    @Test
    void adminMetricsReflectDatabaseAccountStates() throws Exception {
        User admin = account("ADMIN");
        User locked = account("STUDENT");
        jdbc.update("UPDATE m_user SET account_enabled = FALSE, account_locked = TRUE WHERE user_id = ?", locked.id);
        MockHttpSession session = login(admin.email);
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM user_login_events WHERE user_id = ?", Long.class, admin.id));
        JsonNode dashboard = dashboard(session, "/api/dashboard");
        assertStat(dashboard, "accounts", jdbc.queryForObject("SELECT COUNT(*) FROM m_user", Long.class));
        assertStat(dashboard, "enabled", jdbc.queryForObject(
                "SELECT COUNT(*) FROM m_user WHERE account_enabled = TRUE AND account_locked = FALSE", Long.class));
        assertStat(dashboard, "roles", 7);
        assertStat(dashboard, "locked", 1);
        long activeAccountsInChart = 0;
        for (JsonNode point : dashboard.path("chart").path("points")) activeAccountsInChart += point.path("value").asLong();
        assertEquals(1, activeAccountsInChart, "The successful login must appear in the weekly admin chart");
        assertTrue(dashboard.path("panels").toString().contains(admin.email));
    }

    @Test
    void accountantTotalsUseConfirmedPaymentsAndOutstandingOperationalRecords() throws Exception {
        User accountant = account("ACCOUNTANT");
        User student = account("STUDENT");
        UUID active = contract("FINANCE-ACTIVE", student, "ACTIVE", 1_000_000, 200_000);
        contract("FINANCE-DRAFT", student, "DRAFT", 9_000_000, 0);
        contract("FINANCE-CANCELLED", student, "CANCELLED", 9_000_000, 0);
        transaction(active, "TUITION", 600_000, "CONFIRMED");
        transaction(active, "TUITION", 500_000, "PENDING");
        transaction(active, "TUITION", 100_000, "MISMATCH");
        insert("financial_transactions", "contract_id", active, "kind", "TUITION", "amount", 100_000,
                "status", "CONFIRMED", "created_at", OffsetDateTime.now(ZoneId.of("Asia/Ho_Chi_Minh")).minusMonths(1));
        transaction(active, "DEPOSIT", 200_000, "CONFIRMED");
        transaction(active, "REFUND", 50_000, "CONFIRMED");
        transaction(active, "DEPOSIT", 900_000, "PENDING");
        UUID asset = insert("equipment_assets", "code", "DAMAGED-ASSET", "name", "Damaged equipment",
                "status", "MAINTENANCE");
        insert("equipment_incidents", "asset_id", asset, "reported_by", accountant.id,
                "description", "Unpaid damage", "status", "OPEN", "compensation_due", 30_000);
        insert("equipment_incidents", "asset_id", asset, "reported_by", accountant.id,
                "description", "Resolved damage", "status", "RESOLVED", "compensation_due", 40_000);

        JsonNode dashboard = dashboard(login(accountant.email), "/api/dashboard");
        assertStat(dashboard, "tuition", 600_000);
        assertStat(dashboard, "deposits", 150_000);
        assertStat(dashboard, "receivables", 300_000);
        assertStat(dashboard, "compensation", 30_000);
    }

    @Test
    void hrCountsUseRegistrationsFollowupsAndContractsFromDatabase() throws Exception {
        User hr = account("HR");
        User student = account("STUDENT");
        UUID first = consultation("Current registration one", OffsetDateTime.now());
        UUID second = consultation("Current registration two", OffsetDateTime.now());
        UUID old = consultation("Old registration", OffsetDateTime.now().minusWeeks(1));
        insert("consultation_followups", "consultation_id", first, "assigned_to", hr.id, "status", "CONTACTED");
        insert("consultation_followups", "consultation_id", second, "assigned_to", hr.id, "status", "APPOINTMENT");
        insert("consultation_followups", "consultation_id", old, "assigned_to", hr.id, "status", "CLOSED");
        contract("HR-DRAFT", student, "DRAFT", 1_000_000, 0);
        contract("HR-SIGNED", student, "SIGNED", 1_000_000, 0);
        contract("HR-ACTIVE", student, "ACTIVE", 1_000_000, 0);
        contract("HR-COMPLETED", student, "COMPLETED", 1_000_000, 0);
        contract("HR-CANCELLED", student, "CANCELLED", 1_000_000, 0);
        insert("training_contracts", "code", "HR-OLD", "student_id", student.id, "status", "SIGNED",
                "created_at", OffsetDateTime.now().minusWeeks(1));

        JsonNode dashboard = dashboard(login(hr.email), "/api/dashboard");
        assertStat(dashboard, "registrations", 2);
        assertStat(dashboard, "consulting", 2);
        assertStat(dashboard, "drafts", 1);
        assertStat(dashboard, "contracts", 3);
        String registrationRows = dashboard.path("panels").toString();
        assertTrue(registrationRows.contains("Current registration one"));
        assertTrue(registrationRows.contains("Current registration two"));
        double registrationsInChart = 0;
        for (JsonNode point : dashboard.path("chart").path("points")) registrationsInChart += point.path("value").asDouble();
        assertEquals(2, registrationsInChart);
    }

    @Test
    void itSupportInventoryCountsReflectAssetStates() throws Exception {
        User support = account("IT_SUPPORT");
        User student = account("STUDENT");
        for (String state : List.of("AVAILABLE", "ASSIGNED", "MAINTENANCE", "RETIRED")) {
            UUID asset = insert("equipment_assets", "code", "ASSET-" + state, "name", state + " equipment", "status", state);
            if (state.equals("ASSIGNED")) {
                insert("equipment_loans", "asset_id", asset, "student_id", student.id,
                        "received_at", OffsetDateTime.now(), "return_status", "ACTIVE");
            }
        }
        JsonNode dashboard = dashboard(login(support.email), "/api/dashboard");
        assertStat(dashboard, "equipment", 3);
        assertStat(dashboard, "assigned", 1);
        assertStat(dashboard, "available", 1);
        assertStat(dashboard, "maintenance", 1);
    }

    private User account(String roleCode) {
        return account(roleCode, "primary");
    }

    private User account(String roleCode, String suffix) {
        Role role = roles.findByCode(roleCode).orElseGet(
                () -> roles.saveAndFlush(new Role(roleCode, roleCode)));
        User account = new User();
        account.email = "dashboard-" + roleCode.toLowerCase(Locale.ROOT) + "-" + suffix + "@test.local";
        account.fullName = "Dashboard " + roleCode;
        account.role = role;
        account.passwordHash = encoder.encode(PASSWORD);
        return users.saveAndFlush(account);
    }

    private UUID trainingClass(String code, User lecturer) {
        return insert("training_classes", "code", code, "name", code + " name", "lecturer_id", lecturer.id,
                "status", "ACTIVE", "total_sessions", 4);
    }

    private UUID consultation(String name, OffsetDateTime createdAt) {
        return insert("consultation_registrations", "full_name", name, "phone", "0901234567",
                "contact_time", "any", "program_name", "Training program", "created_date", createdAt);
    }

    private UUID enrollment(UUID trainingClass, User student) {
        return insert("training_enrollments", "class_id", trainingClass, "student_id", student.id,
                "status", "ACTIVE", "assignment_status", "CONFIRMED", "records_complete", true);
    }

    private UUID session(UUID trainingClass, String title, OffsetDateTime startsAt) {
        return insert("training_sessions", "class_id", trainingClass, "title", title,
                "starts_at", startsAt, "ends_at", startsAt.plusHours(2), "room", "Training room");
    }

    private UUID contract(String code, User student, String status, long tuition, long deposit) {
        return insert("training_contracts", "code", code, "student_id", student.id, "status", status,
                "tuition_due", tuition, "deposit_due", deposit);
    }

    private void transaction(UUID contract, String kind, long amount, String status) {
        insert("financial_transactions", "contract_id", contract, "kind", kind, "amount", amount, "status", status);
    }

    private void request(User lecturer, UUID trainingClass, String title) {
        insert("equipment_requests", "requested_by", lecturer.id, "class_id", trainingClass, "title", title,
                "quantity", 1, "status", "NEW");
    }

    private UUID insert(String table, Object... fields) {
        Map<String, Object> values = new LinkedHashMap<>();
        UUID id = UUID.randomUUID();
        values.put("id", id);
        for (int i = 0; i < fields.length; i += 2) values.put((String) fields[i], fields[i + 1]);
        String placeholders = String.join(",", java.util.Collections.nCopies(values.size(), "?"));
        jdbc.update("INSERT INTO " + table + " (" + String.join(",", values.keySet()) + ") VALUES ("
                + placeholders + ")", new ArrayList<>(values.values()).toArray());
        return id;
    }

    private static void assertStat(JsonNode dashboard, String key, double expected) {
        for (JsonNode stat : dashboard.path("stats")) {
            if (key.equals(stat.path("key").asText())) {
                assertEquals(expected, stat.path("value").asDouble(), 0.01, key);
                return;
            }
        }
        fail("Missing dashboard statistic: " + key);
    }

    private MockHttpSession login(String email) throws Exception {
        var result = mvc.perform(post("/api/auth/login").with(csrf())
                        .param("username", email).param("password", PASSWORD))
                .andExpect(status().isNoContent()).andReturn();
        MockHttpSession session = (MockHttpSession) result.getRequest().getSession(false);
        assertNotNull(session);
        return session;
    }

    private void assertRejectedRoleLogin(User account) throws Exception {
        long previousLogins = jdbc.queryForObject(
                "SELECT COUNT(*) FROM user_login_events WHERE user_id = ?", Long.class, account.id);
        var result = mvc.perform(post("/api/auth/login").with(csrf())
                        .param("username", account.email).param("password", PASSWORD))
                .andExpect(status().isForbidden()).andReturn();
        JsonNode error = json.readTree(result.getResponse().getContentAsString());
        assertEquals("INVALID_ROLE", error.path("code").asText());
        assertEquals(INVALID_ROLE_MESSAGE, error.path("message").asText());
        var session = result.getRequest().getSession(false);
        if (session != null) assertNull(session.getAttribute("SPRING_SECURITY_CONTEXT"));
        assertEquals(previousLogins, jdbc.queryForObject(
                "SELECT COUNT(*) FROM user_login_events WHERE user_id = ?", Long.class, account.id));
    }

    private JsonNode dashboard(MockHttpSession session, String path) throws Exception {
        return json.readTree(mvc.perform(get(path).session(session))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private static String slug(String role) {
        return role.toLowerCase(Locale.ROOT).replace('_', '-');
    }

    private static void assertContract(JsonNode dashboard) {
        assertFalse(dashboard.path("updatedAt").asText().isBlank());
        for (String key : List.of("stats", "highlights", "panels", "tasks")) {
            assertTrue(dashboard.path(key).isArray(), key);
        }
        assertFalse(dashboard.path("stats").isEmpty());
        for (JsonNode stat : dashboard.path("stats")) {
            if (dashboard.path("role").asText().equals("STUDENT") && stat.path("key").asText().equals("grade")) {
                assertTrue(stat.path("value").isNumber() || stat.path("value").isNull(), stat.toString());
            } else {
                assertTrue(stat.path("value").isNumber(), stat.toString());
            }
            assertFalse(stat.path("key").asText().isBlank());
            assertFalse(stat.path("label").asText().isBlank());
        }
        for (JsonNode panel : dashboard.path("panels")) {
            assertFalse(panel.path("title").asText().isBlank());
            assertTrue(panel.path("columns").isArray());
            assertTrue(panel.path("rows").isArray());
            for (JsonNode row : panel.path("rows")) assertTrue(row.path("cells").isArray());
        }
        assertTrue(dashboard.path("chart").isObject());
        assertTrue(dashboard.path("chart").path("points").isArray());
    }
}

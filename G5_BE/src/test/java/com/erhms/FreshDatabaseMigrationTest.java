package com.erhms;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class FreshDatabaseMigrationTest {
    private static final String PASSWORD = "Demo@123456";

    @Test
    void migrationsCreateFinalSchemaAndRunOnlyOnce() throws Exception {
        String url = databaseUrl();
        Flyway flyway = flyway(url);
        assertEquals(7, flyway.migrate().migrationsExecuted);
        try (Connection connection = connection(url)) {
            assertEquals(Set.of("m_role", "m_user", "recruitment_requests", "request_events",
                            "consultation_registrations", "user_login_events", "training_classes",
                            "training_enrollments", "training_sessions", "training_attendance", "training_grades",
                            "training_contracts", "financial_transactions", "equipment_assets", "equipment_requests",
                            "equipment_loans", "equipment_incidents", "consultation_followups",
                            "password_reset_challenges", "password_reset_rate_limits"),
                    names(connection, """
                            SELECT LOWER(table_name) FROM information_schema.tables
                            WHERE table_schema = CURRENT_SCHEMA()
                              AND LOWER(table_name) <> 'flyway_schema_history'
                            """));
            assertEquals(Set.of("role_id", "code", "name"), columnNames(connection, "m_role"));
            assertEquals(Set.of("user_id", "email", "password_hash", "full_name", "role_id", "phone",
                            "account_enabled", "account_locked", "credentials_version"),
                    columnNames(connection, "m_user"));
            assertEquals(Set.of("email_key", "user_id", "challenge_id", "code_hash", "code_expires_at",
                            "requested_at", "request_window_at", "request_count", "attempts", "token_hash",
                            "token_expires_at", "password_snapshot", "credentials_version", "consumed"),
                    columnNames(connection, "password_reset_challenges"));
            assertEquals(Set.of("client_key", "window_at", "request_count"),
                    columnNames(connection, "password_reset_rate_limits"));
            assertEquals(Set.of("id", "full_name", "phone", "email", "contact_time",
                            "description", "created_date", "program_name"),
                    columnNames(connection, "consultation_registrations"));
            assertEquals(0, scalarLong(connection, """
                    SELECT COUNT(*) FROM information_schema.columns
                    WHERE table_schema = CURRENT_SCHEMA() AND LOWER(column_name) = 'employer_id'
                    """));
            assertBigint(connection, "m_role", "role_id");
            assertBigint(connection, "m_user", "user_id");
            assertBigint(connection, "m_user", "role_id");
            assertBigint(connection, "m_user", "credentials_version");
            assertBigint(connection, "password_reset_challenges", "credentials_version");
            assertBigint(connection, "recruitment_requests", "created_by");
            assertBigint(connection, "request_events", "actor_id");
            assertFixedRoleIds(connection);
            assertEquals(7, scalarLong(connection, "SELECT COUNT(*) FROM m_role"));
            assertEquals(3, scalarLong(connection, "SELECT COUNT(*) FROM m_user"));
            assertEquals(3, scalarLong(connection,
                    "SELECT COUNT(*) FROM m_user WHERE account_enabled = TRUE AND account_locked = FALSE"));
            for (String table : List.of("user_login_events", "training_classes", "training_enrollments",
                    "training_sessions", "training_attendance", "training_grades", "training_contracts",
                    "financial_transactions", "equipment_assets", "equipment_requests", "equipment_loans",
                    "equipment_incidents", "consultation_followups", "password_reset_challenges",
                    "password_reset_rate_limits")) {
                assertEquals(0, scalarLong(connection, "SELECT COUNT(*) FROM " + table), table);
            }
            assertEquals(5, scalarLong(connection, "SELECT COUNT(*) FROM recruitment_requests"));
            long eventCount = scalarLong(connection, "SELECT COUNT(*) FROM request_events");
            assertEquals(13, eventCount);
            assertEquals(0, flyway.migrate().migrationsExecuted);
            assertEquals(7, scalarLong(connection, "SELECT COUNT(*) FROM m_role"));
            assertEquals(3, scalarLong(connection, "SELECT COUNT(*) FROM m_user"));
            assertEquals(5, scalarLong(connection, "SELECT COUNT(*) FROM recruitment_requests"));
            assertEquals(eventCount, scalarLong(connection, "SELECT COUNT(*) FROM request_events"));
        }
    }

    @Test
    void existingSchemaWithoutFlywayHistoryIsBaselinedWithoutChangingExistingRows() throws Exception {
        String url = databaseUrl();
        Flyway.configure().dataSource(url, "sa", "")
                .locations("classpath:db/migration").target("1").load().migrate();
        try (Connection connection = connection(url)) {
            execute(connection, "DROP TABLE \"flyway_schema_history\"");
            execute(connection, "UPDATE m_user SET full_name = 'Existing user' WHERE email = 'user1@erhms.local'");
        }
        var upgrade = Flyway.configure().dataSource(url, "sa", "")
                .locations("classpath:db/migration").baselineOnMigrate(true).baselineVersion("1").load();
        assertEquals(6, upgrade.migrate().migrationsExecuted);
        try (Connection connection = connection(url)) {
            assertEquals(3, scalarLong(connection, "SELECT COUNT(*) FROM m_user"));
            assertEquals(1, scalarLong(connection, "SELECT COUNT(*) FROM m_user WHERE full_name = 'Existing user'"));
            assertEquals(5, scalarLong(connection, "SELECT COUNT(*) FROM recruitment_requests"));
            assertEquals(13, scalarLong(connection, "SELECT COUNT(*) FROM request_events"));
            assertEquals(0, scalarLong(connection, "SELECT COUNT(*) FROM consultation_registrations"));
            assertEquals(0, upgrade.migrate().migrationsExecuted);
        }
    }

    @Test
    void consultationUpgradePreservesExistingDetailsAndConvertsSelectionCodesToDisplayText() throws Exception {
        String url = databaseUrl();
        Flyway.configure().dataSource(url, "sa", "")
                .locations("classpath:db/migration").target("2").load().migrate();
        Map<String, String> labels = new LinkedHashMap<>();
        labels.put("not-sure", "Tôi cần tư vấn ngành nghề và lộ trình học");
        labels.put("safety", "An toàn trong môi trường sản xuất");
        labels.put("electric", "Điện công nghiệp nền tảng");
        labels.put("quality", "Kiểm tra chất lượng cơ bản");
        labels.put("career-electric", "Điện công nghiệp");
        labels.put("career-mechanical", "Cơ khí & lắp ráp");
        labels.put("career-welding", "Kỹ thuật hàn cơ bản");
        labels.put("career-production", "Vận hành & lắp ráp sản xuất");
        labels.put("career-sewing", "May công nghiệp");
        labels.put("career-quality", "Kiểm tra chất lượng sản phẩm");
        Map<String, UUID> existingIds = new LinkedHashMap<>();
        OffsetDateTime createdDate = OffsetDateTime.parse("2026-10-17T10:15:30.123456+07:00");
        try (Connection connection = connection(url)) {
            for (String code : labels.keySet()) {
                UUID id = UUID.randomUUID();
                existingIds.put(code, id);
                execute(connection, """
                        INSERT INTO consultation_registrations
                            (id, kind, full_name, phone, email, interest, contact_time, message, consent, created_at)
                        VALUES (?, 'training', ?, ?, ?, ?, 'afternoon', ?, TRUE, ?)
                        """, id, "Người đăng ký " + code, "0901234567",
                        code.equals("not-sure") ? null : "existing@example.com", code,
                        code.equals("not-sure") ? null : "Nội dung tư vấn đã nhập", createdDate);
            }
        }

        Flyway upgrade = flyway(url);
        assertEquals(5, upgrade.migrate().migrationsExecuted);
        try (Connection connection = connection(url)) {
            assertEquals(Set.of("id", "full_name", "phone", "email", "contact_time",
                            "description", "created_date", "program_name"),
                    columnNames(connection, "consultation_registrations"));
            assertEquals(labels.size(), scalarLong(connection, "SELECT COUNT(*) FROM consultation_registrations"));
            for (var entry : existingIds.entrySet()) {
                String code = entry.getKey();
                try (var statement = connection.prepareStatement("""
                        SELECT id, full_name, phone, email, contact_time, description, created_date, program_name
                        FROM consultation_registrations WHERE id = ?
                        """)) {
                    statement.setObject(1, entry.getValue());
                    try (ResultSet result = statement.executeQuery()) {
                        assertTrue(result.next(), code);
                        assertEquals(entry.getValue(), result.getObject("id", UUID.class));
                        assertEquals("Người đăng ký " + code, result.getString("full_name"));
                        assertEquals("0901234567", result.getString("phone"));
                        assertEquals(code.equals("not-sure") ? null : "existing@example.com", result.getString("email"));
                        assertEquals("afternoon", result.getString("contact_time"));
                        assertEquals(code.equals("not-sure") ? null : "Nội dung tư vấn đã nhập",
                                result.getString("description"));
                        assertEquals(createdDate.toInstant(),
                                result.getObject("created_date", OffsetDateTime.class).toInstant());
                        assertEquals(labels.get(code), result.getString("program_name"));
                        assertFalse(result.next());
                    }
                }
            }
            String newProgram = "Tên chương trình mới ngoài danh sách mã cũ";
            execute(connection, """
                    INSERT INTO consultation_registrations
                        (id, full_name, phone, contact_time, created_date, program_name)
                    VALUES (?, 'New registration', '0901234567', 'any', ?, ?)
                    """, UUID.randomUUID(), createdDate, newProgram);
            assertEquals(labels.size() + 1, scalarLong(connection, "SELECT COUNT(*) FROM consultation_registrations"));
            assertIntegrityViolation(connection, "UPDATE consultation_registrations SET program_name = NULL");
            assertEquals(0, upgrade.migrate().migrationsExecuted);
        }
    }

    @Test
    void userPhoneUpgradePreservesExistingAccountsAndAllowsOptionalPhoneNumbers() throws Exception {
        String url = databaseUrl();
        Flyway.configure().dataSource(url, "sa", "")
                .locations("classpath:db/migration").target("3").load().migrate();
        Map<Long, List<Object>> existingUsers;
        try (Connection connection = connection(url)) {
            execute(connection, "UPDATE m_user SET full_name = 'Existing user' WHERE email = 'user1@erhms.local'");
            existingUsers = userDetails(connection);
        }

        Flyway upgrade = Flyway.configure().dataSource(url, "sa", "")
                .locations("classpath:db/migration").target("4").load();
        assertEquals(1, upgrade.migrate().migrationsExecuted);
        try (Connection connection = connection(url)) {
            assertEquals(existingUsers, userDetails(connection));
            assertEquals(existingUsers.size(), scalarLong(connection, "SELECT COUNT(*) FROM m_user WHERE phone IS NULL"));
            try (Statement statement = connection.createStatement();
                 ResultSet result = statement.executeQuery("SELECT phone FROM m_user WHERE 1 = 0")) {
                var metadata = result.getMetaData();
                assertEquals(Types.VARCHAR, metadata.getColumnType(1));
                assertEquals(20, metadata.getPrecision(1));
                assertEquals(java.sql.ResultSetMetaData.columnNullable, metadata.isNullable(1));
            }

            execute(connection, "UPDATE m_user SET phone = ? WHERE email = ?", "+84901234567", "user1@erhms.local");
            assertEquals(Set.of("+84901234567"), names(connection,
                    "SELECT phone FROM m_user WHERE email = 'user1@erhms.local'"));
            assertEquals(existingUsers, userDetails(connection));
            execute(connection, "UPDATE m_user SET phone = NULL WHERE email = 'user1@erhms.local'");
            assertEquals(existingUsers.size(), scalarLong(connection, "SELECT COUNT(*) FROM m_user WHERE phone IS NULL"));
            assertEquals(0, upgrade.migrate().migrationsExecuted);
        }
    }

    @Test
    void demoAccountsAndRequestsHaveUsableCredentialsAndConsistentOwnership() throws Exception {
        String url = databaseUrl();
        flyway(url).migrate();
        try (Connection connection = connection(url)) {
            assertEquals(Set.of("ADMIN", "LECTURER", "STUDENT", "ACCOUNTANT",
                    "IT_SUPPORT", "MANAGER", "HR"), names(connection, "SELECT code FROM m_role"));
            var encoder = new BCryptPasswordEncoder();
            Set<String> accounts = new HashSet<>();
            try (Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery("""
                    SELECT u.user_id, u.email, u.password_hash, u.full_name, r.code
                    FROM m_user u JOIN m_role r ON r.role_id = u.role_id
                    """)) {
                while (result.next()) {
                    String email = result.getString("email");
                    accounts.add(email);
                    assertTrue(result.getLong("user_id") > 0);
                    assertFalse(result.getString("full_name").isBlank());
                    assertTrue(encoder.matches(PASSWORD, result.getString("password_hash")), email);
                    assertEquals(email.equals("manager@erhms.local") ? "MANAGER" : "HR",
                            result.getString("code"));
                }
            }
            assertEquals(Set.of("user1@erhms.local", "user2@erhms.local", "manager@erhms.local"), accounts);

            Map<String, String> expectedStates = new LinkedHashMap<>();
            expectedStates.put("00000000-0000-0000-0000-000000000101", "DRAFT");
            expectedStates.put("00000000-0000-0000-0000-000000000102", "SUBMITTED");
            expectedStates.put("00000000-0000-0000-0000-000000000103", "REVISION_REQUIRED");
            expectedStates.put("00000000-0000-0000-0000-000000000104", "APPROVED");
            expectedStates.put("00000000-0000-0000-0000-000000000105", "CLOSED");
            Map<String, String> lastActions = Map.of(
                    "DRAFT", "CREATED", "SUBMITTED", "SUBMIT",
                    "REVISION_REQUIRED", "REQUEST-REVISION", "APPROVED", "APPROVE", "CLOSED", "CLOSE");
            Map<String, Long> expectedVersions = Map.of(
                    "DRAFT", 0L, "SUBMITTED", 1L, "REVISION_REQUIRED", 2L, "APPROVED", 2L, "CLOSED", 3L);
            LocalDate today;
            try (Statement statement = connection.createStatement();
                 ResultSet result = statement.executeQuery("SELECT CURRENT_DATE")) {
                assertTrue(result.next());
                today = result.getObject(1, LocalDate.class);
            }
            try (Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery("""
                    SELECT r.id, r.status, r.version, r.needed_by, r.review_note, r.created_at, r.updated_at, u.email
                    FROM recruitment_requests r JOIN m_user u ON u.user_id = r.created_by ORDER BY r.id
                    """)) {
                int index = 0;
                while (result.next()) {
                    String id = result.getObject("id", UUID.class).toString();
                    assertEquals(expectedStates.get(id), result.getString("status"));
                    assertEquals(expectedVersions.get(result.getString("status")), result.getLong("version"));
                    try (var history = connection.prepareStatement("""
                            SELECT action FROM request_events WHERE request_id = ?
                            ORDER BY occurred_at DESC, id DESC FETCH FIRST ROW ONLY
                            """)) {
                        history.setObject(1, UUID.fromString(id));
                        try (ResultSet latestEvent = history.executeQuery()) {
                            assertTrue(latestEvent.next());
                            assertEquals(lastActions.get(result.getString("status")), latestEvent.getString(1));
                        }
                    }
                    assertEquals(index < 3 ? "user1@erhms.local" : "user2@erhms.local",
                            result.getString("email"));
                    assertFalse(result.getObject("needed_by", LocalDate.class).isBefore(today));
                    assertFalse(result.getTimestamp("created_at").after(result.getTimestamp("updated_at")));
                    if ("REVISION_REQUIRED".equals(result.getString("status"))) {
                        assertNotNull(result.getString("review_note"));
                        assertFalse(result.getString("review_note").isBlank());
                    }
                    index++;
                }
                assertEquals(5, index);
            }
            assertEquals(5, scalarLong(connection, """
                    SELECT COUNT(*) FROM recruitment_requests r
                    WHERE EXISTS (SELECT 1 FROM request_events e
                                  WHERE e.request_id = r.id AND e.actor_id = r.created_by AND e.action = 'CREATED')
                    """));
            assertEquals(0, scalarLong(connection, """
                    SELECT COUNT(*) FROM request_events e JOIN m_user u ON u.user_id = e.actor_id
                    JOIN m_role r ON r.role_id = u.role_id
                    WHERE e.action IN ('APPROVE', 'REQUEST-REVISION', 'CLOSE') AND r.code <> 'MANAGER'
                    """));
            assertEquals(0, scalarLong(connection, """
                    SELECT COUNT(*) FROM request_events e JOIN recruitment_requests r ON r.id = e.request_id
                    WHERE e.action IN ('CREATED', 'SUBMIT') AND e.actor_id <> r.created_by
                    """));
            assertEquals(0, scalarLong(connection, """
                    SELECT COUNT(*) FROM recruitment_requests r
                    WHERE (SELECT COUNT(*) FROM request_events e WHERE e.request_id = r.id) <> r.version + 1
                    """));
        }
    }

    @Test
    void sequencesContinueAfterDemoRowsWithoutDuplicateIds() throws Exception {
        String url = databaseUrl();
        flyway(url).migrate();
        try (Connection connection = connection(url)) {
            long previousRoleId = scalarLong(connection, "SELECT MAX(role_id) FROM m_role");
            long previousUserId = scalarLong(connection, "SELECT MAX(user_id) FROM m_user");
            execute(connection, "INSERT INTO m_role (code, name) VALUES ('FIRST_NEW_ROLE', 'First role')");
            execute(connection, "INSERT INTO m_role (code, name) VALUES ('SECOND_NEW_ROLE', 'Second role')");
            long firstRoleId = scalarLong(connection,
                    "SELECT role_id FROM m_role WHERE code = 'FIRST_NEW_ROLE'");
            long secondRoleId = scalarLong(connection,
                    "SELECT role_id FROM m_role WHERE code = 'SECOND_NEW_ROLE'");
            assertTrue(firstRoleId > previousRoleId);
            assertEquals(firstRoleId + 1, secondRoleId);
            execute(connection, """
                    INSERT INTO m_user (email, password_hash, full_name, role_id)
                    VALUES ('first-new@example.com', 'new-hash', 'First User', ?)
                    """, firstRoleId);
            execute(connection, """
                    INSERT INTO m_user (email, password_hash, full_name, role_id)
                    VALUES ('second-new@example.com', 'new-hash', 'Second User', ?)
                    """, firstRoleId);
            long firstUserId = scalarLong(connection,
                    "SELECT user_id FROM m_user WHERE email = 'first-new@example.com'");
            long secondUserId = scalarLong(connection,
                    "SELECT user_id FROM m_user WHERE email = 'second-new@example.com'");
            assertTrue(firstUserId > previousUserId);
            assertEquals(firstUserId + 1, secondUserId);
            assertEquals(5, scalarLong(connection, "SELECT COUNT(DISTINCT user_id) FROM m_user"));
            assertEquals(9, scalarLong(connection, "SELECT COUNT(DISTINCT role_id) FROM m_role"));
        }
    }

    @Test
    void foreignKeysAndBusinessConstraintsProtectTheSeededSchema() throws Exception {
        String url = databaseUrl();
        flyway(url).migrate();
        try (Connection connection = connection(url)) {
            assertIntegrityViolation(connection, "UPDATE m_user SET role_id = NULL");
            assertIntegrityViolation(connection, "UPDATE m_user SET role_id = 9223372036854775807");
            assertIntegrityViolation(connection, "DELETE FROM m_role WHERE code = 'HR'");
            assertIntegrityViolation(connection, "DELETE FROM m_user WHERE email = 'user1@erhms.local'");
            assertIntegrityViolation(connection, "DELETE FROM m_user WHERE email = 'manager@erhms.local'");
            assertIntegrityViolation(connection, "UPDATE recruitment_requests SET created_by = NULL");
            assertIntegrityViolation(connection, "UPDATE recruitment_requests SET created_by = 9223372036854775807");
            assertIntegrityViolation(connection, "UPDATE request_events SET actor_id = NULL");
            assertIntegrityViolation(connection, "UPDATE request_events SET actor_id = 9223372036854775807");
            assertIntegrityViolation(connection,
                    "UPDATE request_events SET request_id = '99999999-0000-0000-0000-000000000001'");
            assertIntegrityViolation(connection, "INSERT INTO m_role (code, name) VALUES ('HR', 'Duplicate')");
            assertIntegrityViolation(connection, """
                    INSERT INTO m_user (email, password_hash, full_name, role_id)
                    SELECT 'user1@erhms.local', 'duplicate-hash', 'Duplicate', role_id
                    FROM m_role WHERE code = 'HR'
                    """);
            assertIntegrityViolation(connection, "UPDATE recruitment_requests SET quantity = 0");
            assertIntegrityViolation(connection, "UPDATE recruitment_requests SET quantity = 10001");
            assertIntegrityViolation(connection, "UPDATE recruitment_requests SET shift = 'INVALID'");
            assertIntegrityViolation(connection, "UPDATE recruitment_requests SET status = 'INVALID'");
        }
    }

    @Test
    void historicalRoleUpgradePreservesAccountsAndUnknownRolesWhileRemappingKnownMeanings() throws Exception {
        String url = databaseUrl();
        migrateThroughVersionFive(url);
        Map<Long, List<Object>> expectedUsers;
        Map<Long, Long> newIds = Map.of(1L, 7L, 2L, 6L, 3L, 1L, 4L, 2L, 5L, 3L,
                6L, 4L, 7L, 5L, 8L, 6L, 9L, 7L, 11L, 7L);
        try (Connection connection = connection(url)) {
            execute(connection, "INSERT INTO m_role (role_id, code, name) VALUES (10, 'OUTSIDE', 'Keep custom role')");
            execute(connection, "INSERT INTO m_role (role_id, code, name) VALUES (11, 'EMPLOYER_HR', 'Legacy recruiter')");
            for (long roleId = 1; roleId <= 11; roleId++) {
                execute(connection, """
                        INSERT INTO m_user (email, password_hash, full_name, role_id, phone)
                        VALUES (?, 'existing-hash', 'Preserved account', ?, '0901234567')
                        """, "role-" + roleId + "@example.com", roleId);
            }
            execute(connection, "UPDATE m_user SET account_enabled = FALSE, account_locked = TRUE WHERE role_id = 10");
            expectedUsers = userDetails(connection);
            expectedUsers.replaceAll((id, values) -> List.of(values.get(0), values.get(1), values.get(2),
                    newIds.getOrDefault((Long) values.get(3), (Long) values.get(3))));
        }

        Flyway upgrade = flywayThroughVersionSix(url);
        assertEquals(1, upgrade.migrate().migrationsExecuted);
        try (Connection connection = connection(url)) {
            assertFixedRoleIds(connection);
            assertEquals(expectedUsers, userDetails(connection));
            assertEquals(8, scalarLong(connection, "SELECT COUNT(*) FROM m_role"));
            assertEquals(Set.of("Keep custom role"), names(connection, "SELECT name FROM m_role WHERE role_id = 10"));
            assertEquals(1, scalarLong(connection, """
                    SELECT COUNT(*) FROM m_user WHERE email = 'role-10@example.com' AND role_id = 10
                    AND account_enabled = FALSE AND account_locked = TRUE AND phone = '0901234567'
                    """));
            assertEquals(11, scalarLong(connection, "SELECT COUNT(*) FROM m_user WHERE phone = '0901234567'"));
            assertEquals(0, upgrade.migrate().migrationsExecuted);
        }
    }

    @Test
    void manuallyFixedRoleIdsAndUnsupportedExtrasAreLeftUnchanged() throws Exception {
        String url = databaseUrl();
        migrateThroughVersionFive(url);
        Map<Long, List<Object>> usersBefore;
        Map<Long, List<String>> rolesBefore;
        try (Connection connection = connection(url)) {
            // Keep user rows and their request/event ownership intact while modeling the user's manual setup.
            execute(connection, "UPDATE m_user SET role_id = 9");
            execute(connection, "DELETE FROM m_role WHERE role_id <> 9");
            execute(connection, "UPDATE m_role SET code = 'TEMP_HR' WHERE role_id = 9");
            List<String> codes = List.of("ADMIN", "LECTURER", "STUDENT", "ACCOUNTANT", "IT_SUPPORT", "MANAGER", "HR");
            for (int index = 0; index < codes.size(); index++) {
                execute(connection, "INSERT INTO m_role (role_id, code, name) VALUES (?, ?, ?)",
                        index + 1L, codes.get(index), "Existing label " + codes.get(index));
            }
            execute(connection, "UPDATE m_user SET role_id = 7");
            execute(connection, "UPDATE m_user SET role_id = 6 WHERE email = 'manager@erhms.local'");
            execute(connection, "DELETE FROM m_role WHERE role_id = 9");
            execute(connection, "INSERT INTO m_role (role_id, code, name) VALUES (8, 'OUTSIDE', 'Unsupported role')");
            execute(connection, "INSERT INTO m_role (role_id, code, name) VALUES (9, 'USER', 'Unsupported legacy role')");
            execute(connection, """
                    INSERT INTO m_user (email, password_hash, full_name, role_id)
                    VALUES ('outside@example.com', 'existing-hash', 'Outside account', 8)
                    """);
            usersBefore = userDetails(connection);
            rolesBefore = roleDetails(connection);
        }

        assertEquals(1, flywayThroughVersionSix(url).migrate().migrationsExecuted);
        try (Connection connection = connection(url)) {
            assertFixedRoleIds(connection);
            assertEquals(usersBefore, userDetails(connection));
            assertEquals(rolesBefore, roleDetails(connection));
        }
    }

    @Test
    void arbitraryRoleIdsAreNotGrantedCanonicalPermissionsByMigration() throws Exception {
        String url = databaseUrl();
        migrateThroughVersionFive(url);
        Map<Long, List<Object>> usersBefore;
        Map<Long, List<String>> rolesBefore;
        try (Connection connection = connection(url)) {
            // An ADMIN code at ID 8 is invalid under the fixed-ID contract and must stay invalid.
            execute(connection, "UPDATE m_role SET code = 'OLD_ADMIN' WHERE role_id = 3");
            execute(connection, "UPDATE m_role SET code = 'OLD_MANAGER' WHERE role_id = 8");
            execute(connection, "UPDATE m_role SET code = 'ADMIN' WHERE role_id = 8");
            execute(connection, "UPDATE m_user SET role_id = 8 WHERE email = 'user1@erhms.local'");
            usersBefore = userDetails(connection);
            rolesBefore = roleDetails(connection);
        }

        assertEquals(1, flywayThroughVersionSix(url).migrate().migrationsExecuted);
        try (Connection connection = connection(url)) {
            assertEquals(usersBefore, userDetails(connection));
            assertEquals(rolesBefore, roleDetails(connection));
            assertEquals(8, scalarLong(connection, "SELECT role_id FROM m_role WHERE code = 'ADMIN'"));
        }
    }

    @Test
    void passwordRecoveryUpgradePreservesAllExistingRowsAndCreatesNoResetSecrets() throws Exception {
        String url = databaseUrl();
        flywayThroughVersionSix(url).migrate();
        Map<String, List<List<String>>> before;
        try (Connection connection = connection(url)) {
            long learner = scalarLong(connection, "SELECT user_id FROM m_user WHERE email = 'user1@erhms.local'");
            long lecturer = scalarLong(connection, "SELECT user_id FROM m_user WHERE email = 'manager@erhms.local'");
            execute(connection, "UPDATE m_user SET phone = '0901234567' WHERE user_id = ?", learner);
            execute(connection, "UPDATE m_user SET account_enabled = FALSE, account_locked = TRUE WHERE user_id = ?", lecturer);
            UUID consultation = UUID.randomUUID(), trainingClass = UUID.randomUUID(), enrollment = UUID.randomUUID();
            UUID session = UUID.randomUUID(), contract = UUID.randomUUID(), equipment = UUID.randomUUID();
            OffsetDateTime instant = OffsetDateTime.parse("2026-10-28T09:00:00+07:00");
            execute(connection, "INSERT INTO user_login_events (id, user_id, occurred_at) VALUES (?, ?, ?)",
                    UUID.randomUUID(), learner, instant);
            execute(connection, """
                    INSERT INTO consultation_registrations
                        (id, full_name, phone, email, contact_time, description, created_date, program_name)
                    VALUES (?, 'Existing learner', '0901234567', 'existing@example.com', 'morning',
                        'Keep consultation details', ?, 'Điện công nghiệp')
                    """, consultation, instant);
            execute(connection, """
                    INSERT INTO training_classes (id, code, name, lecturer_id, status, total_sessions)
                    VALUES (?, 'MIGRATION-CLASS', 'Lớp hiện có', ?, 'ACTIVE', 20)
                    """, trainingClass, lecturer);
            execute(connection, """
                    INSERT INTO training_enrollments (id, class_id, student_id, status, assignment_status, records_complete)
                    VALUES (?, ?, ?, 'ACTIVE', 'CONFIRMED', TRUE)
                    """, enrollment, trainingClass, learner);
            execute(connection, """
                    INSERT INTO training_sessions (id, class_id, title, starts_at, ends_at, room, attendance_finalized)
                    VALUES (?, ?, 'Buổi học hiện có', ?, ?, 'Phòng 1', TRUE)
                    """, session, trainingClass, instant, instant.plusHours(2));
            execute(connection, "INSERT INTO training_attendance (id, session_id, student_id, status) VALUES (?, ?, ?, 'PRESENT')",
                    UUID.randomUUID(), session, learner);
            execute(connection, "INSERT INTO training_grades (id, enrollment_id, title, score, published_at) VALUES (?, ?, 'Bài kiểm tra', 8.50, ?)",
                    UUID.randomUUID(), enrollment, instant);
            execute(connection, """
                    INSERT INTO training_contracts (id, code, student_id, consultation_id, tuition_due, deposit_due, status)
                    VALUES (?, 'MIGRATION-CONTRACT', ?, ?, 12000000, 1000000, 'ACTIVE')
                    """, contract, learner, consultation);
            execute(connection, """
                    INSERT INTO financial_transactions (id, contract_id, kind, amount, status)
                    VALUES (?, ?, 'TUITION', 5000000, 'CONFIRMED')
                    """, UUID.randomUUID(), contract);
            execute(connection, "INSERT INTO equipment_assets (id, code, name, status) VALUES (?, 'MIGRATION-ASSET', 'Bộ thực hành điện', 'ASSIGNED')",
                    equipment);
            execute(connection, """
                    INSERT INTO equipment_requests (id, requested_by, class_id, title, quantity, status)
                    VALUES (?, ?, ?, 'Yêu cầu hiện có', 2, 'DELIVERED')
                    """, UUID.randomUUID(), lecturer, trainingClass);
            execute(connection, """
                    INSERT INTO equipment_loans (id, asset_id, student_id, class_id, received_at, deposit_amount)
                    VALUES (?, ?, ?, ?, ?, 1000000)
                    """, UUID.randomUUID(), equipment, learner, trainingClass, instant);
            execute(connection, """
                    INSERT INTO equipment_incidents (id, asset_id, reported_by, description, status, compensation_due)
                    VALUES (?, ?, ?, 'Mô tả sự cố hiện có', 'IN_PROGRESS', 250000)
                    """, UUID.randomUUID(), equipment, lecturer);
            execute(connection, """
                    INSERT INTO consultation_followups (id, consultation_id, assigned_to, status, appointment_at)
                    VALUES (?, ?, ?, 'APPOINTMENT', ?)
                    """, UUID.randomUUID(), consultation, lecturer, instant.plusDays(1));
            before = preRecoverySnapshot(connection);
            for (var entry : before.entrySet()) assertFalse(entry.getValue().isEmpty(), entry.getKey());
        }

        Flyway upgrade = flyway(url);
        assertEquals(1, upgrade.migrate().migrationsExecuted);
        try (Connection connection = connection(url)) {
            assertEquals(before, preRecoverySnapshot(connection));
            assertEquals(3, scalarLong(connection, "SELECT COUNT(*) FROM m_user WHERE credentials_version = 0"));
            assertEquals(0, scalarLong(connection, "SELECT COUNT(*) FROM password_reset_challenges"));
            assertEquals(0, scalarLong(connection, "SELECT COUNT(*) FROM password_reset_rate_limits"));
            assertIntegrityViolation(connection, "UPDATE m_user SET credentials_version = NULL");
            execute(connection, """
                    INSERT INTO m_user (email, password_hash, full_name, role_id)
                    VALUES ('new-after-v7@example.com', 'existing-test-hash', 'New account', 3)
                    """);
            assertEquals(0, scalarLong(connection,
                    "SELECT credentials_version FROM m_user WHERE email = 'new-after-v7@example.com'"));
            assertEquals(0, upgrade.migrate().migrationsExecuted);
        }
    }

    private static Map<String, List<List<String>>> preRecoverySnapshot(Connection connection) throws SQLException {
        Map<String, List<List<String>>> tables = new LinkedHashMap<>();
        for (String table : names(connection, """
                SELECT LOWER(table_name) FROM information_schema.tables
                WHERE table_schema = CURRENT_SCHEMA()
                    AND LOWER(table_name) NOT IN ('flyway_schema_history', 'password_reset_challenges', 'password_reset_rate_limits')
                """)) {
            String columns = table.equals("m_user")
                    ? "user_id, email, password_hash, full_name, role_id, phone, account_enabled, account_locked" : "*";
            List<List<String>> rows = new ArrayList<>();
            try (Statement statement = connection.createStatement();
                 ResultSet result = statement.executeQuery("SELECT " + columns + " FROM " + table + " ORDER BY 1")) {
                while (result.next()) {
                    List<String> row = new ArrayList<>();
                    for (int index = 1; index <= result.getMetaData().getColumnCount(); index++) row.add(result.getString(index));
                    rows.add(row);
                }
            }
            tables.put(table, rows);
        }
        return tables;
    }

    private static void migrateThroughVersionFive(String url) {
        Flyway.configure().dataSource(url, "sa", "")
                .locations("classpath:db/migration").target("5").load().migrate();
    }

    private static Flyway flywayThroughVersionSix(String url) {
        return Flyway.configure().dataSource(url, "sa", "")
                .locations("classpath:db/migration").target("6").load();
    }

    private static void assertFixedRoleIds(Connection connection) throws SQLException {
        assertEquals(Map.of(1L, List.of("ADMIN"), 2L, List.of("LECTURER"), 3L, List.of("STUDENT"),
                        4L, List.of("ACCOUNTANT"), 5L, List.of("IT_SUPPORT"), 6L, List.of("MANAGER"), 7L, List.of("HR")),
                roleCodes(connection));
    }

    private static Map<Long, List<String>> roleCodes(Connection connection) throws SQLException {
        Map<Long, List<String>> roles = new LinkedHashMap<>();
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("SELECT role_id, code FROM m_role WHERE role_id BETWEEN 1 AND 7 ORDER BY role_id")) {
            while (result.next()) roles.put(result.getLong(1), List.of(result.getString(2)));
        }
        return roles;
    }

    private static Map<Long, List<String>> roleDetails(Connection connection) throws SQLException {
        Map<Long, List<String>> roles = new LinkedHashMap<>();
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("SELECT role_id, code, name FROM m_role ORDER BY role_id")) {
            while (result.next()) roles.put(result.getLong(1), List.of(result.getString(2), result.getString(3)));
        }
        return roles;
    }

    private static Map<Long, List<Object>> userDetails(Connection connection) throws SQLException {
        Map<Long, List<Object>> users = new LinkedHashMap<>();
        try (Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery("""
                SELECT user_id, email, password_hash, full_name, role_id FROM m_user ORDER BY user_id
                """)) {
            while (result.next()) {
                users.put(result.getLong("user_id"), List.of(result.getString("email"),
                        result.getString("password_hash"), result.getString("full_name"), result.getLong("role_id")));
            }
        }
        return users;
    }

    private static Set<String> columnNames(Connection connection, String table) throws SQLException {
        return names(connection, "SELECT LOWER(column_name) FROM information_schema.columns"
                + " WHERE table_schema = CURRENT_SCHEMA() AND LOWER(table_name) = '" + table + "'");
    }

    private static Set<String> names(Connection connection, String sql) throws SQLException {
        Set<String> names = new HashSet<>();
        try (Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery(sql)) {
            while (result.next()) names.add(result.getString(1));
        }
        return names;
    }

    private static void assertBigint(Connection connection, String table, String column) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("SELECT " + column + " FROM " + table + " WHERE 1 = 0")) {
            assertEquals(Types.BIGINT, result.getMetaData().getColumnType(1), table + "." + column);
        }
    }

    private static long scalarLong(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery(sql)) {
            assertTrue(result.next());
            return result.getLong(1);
        }
    }

    private static void execute(Connection connection, String sql, Object... values) throws SQLException {
        try (var statement = connection.prepareStatement(sql)) {
            for (int index = 0; index < values.length; index++) statement.setObject(index + 1, values[index]);
            statement.executeUpdate();
        }
    }

    private static void assertIntegrityViolation(Connection connection, String sql) {
        SQLException exception = assertThrows(SQLException.class, () -> execute(connection, sql));
        assertTrue(exception.getSQLState().startsWith("23"), exception.getMessage());
    }

    private static String databaseUrl() {
        return "jdbc:h2:mem:fresh_database_" + UUID.randomUUID() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1";
    }

    private static Connection connection(String url) throws SQLException {
        return DriverManager.getConnection(url, "sa", "");
    }

    private static Flyway flyway(String url) {
        return Flyway.configure().dataSource(url, "sa", "")
                .locations("classpath:db/migration").load();
    }
}

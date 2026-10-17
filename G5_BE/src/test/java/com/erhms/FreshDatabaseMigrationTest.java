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
        assertEquals(4, flyway.migrate().migrationsExecuted);
        try (Connection connection = connection(url)) {
            assertEquals(Set.of("m_role", "m_user", "recruitment_requests", "request_events",
                            "consultation_registrations"),
                    names(connection, """
                            SELECT LOWER(table_name) FROM information_schema.tables
                            WHERE table_schema = CURRENT_SCHEMA()
                              AND LOWER(table_name) <> 'flyway_schema_history'
                            """));
            assertEquals(Set.of("role_id", "code", "name"), columnNames(connection, "m_role"));
            assertEquals(Set.of("user_id", "email", "password_hash", "full_name", "role_id", "phone"),
                    columnNames(connection, "m_user"));
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
            assertBigint(connection, "recruitment_requests", "created_by");
            assertBigint(connection, "request_events", "actor_id");
            assertEquals(2, scalarLong(connection, "SELECT COUNT(*) FROM m_role"));
            assertEquals(3, scalarLong(connection, "SELECT COUNT(*) FROM m_user"));
            assertEquals(5, scalarLong(connection, "SELECT COUNT(*) FROM recruitment_requests"));
            long eventCount = scalarLong(connection, "SELECT COUNT(*) FROM request_events");
            assertEquals(13, eventCount);
            assertEquals(0, flyway.migrate().migrationsExecuted);
            assertEquals(2, scalarLong(connection, "SELECT COUNT(*) FROM m_role"));
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
        assertEquals(3, upgrade.migrate().migrationsExecuted);
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
        assertEquals(2, upgrade.migrate().migrationsExecuted);
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

        Flyway upgrade = flyway(url);
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
            assertEquals(Set.of("USER", "SERVICE_MANAGER"), names(connection, "SELECT code FROM m_role"));
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
                    assertEquals(email.equals("manager@erhms.local") ? "SERVICE_MANAGER" : "USER",
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
                    WHERE e.action IN ('APPROVE', 'REQUEST-REVISION', 'CLOSE') AND r.code <> 'SERVICE_MANAGER'
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
            assertEquals(4, scalarLong(connection, "SELECT COUNT(DISTINCT role_id) FROM m_role"));
        }
    }

    @Test
    void foreignKeysAndBusinessConstraintsProtectTheSeededSchema() throws Exception {
        String url = databaseUrl();
        flyway(url).migrate();
        try (Connection connection = connection(url)) {
            assertIntegrityViolation(connection, "UPDATE m_user SET role_id = NULL");
            assertIntegrityViolation(connection, "UPDATE m_user SET role_id = 9223372036854775807");
            assertIntegrityViolation(connection, "DELETE FROM m_role WHERE code = 'USER'");
            assertIntegrityViolation(connection, "DELETE FROM m_user WHERE email = 'user1@erhms.local'");
            assertIntegrityViolation(connection, "DELETE FROM m_user WHERE email = 'manager@erhms.local'");
            assertIntegrityViolation(connection, "UPDATE recruitment_requests SET created_by = NULL");
            assertIntegrityViolation(connection, "UPDATE recruitment_requests SET created_by = 9223372036854775807");
            assertIntegrityViolation(connection, "UPDATE request_events SET actor_id = NULL");
            assertIntegrityViolation(connection, "UPDATE request_events SET actor_id = 9223372036854775807");
            assertIntegrityViolation(connection,
                    "UPDATE request_events SET request_id = '99999999-0000-0000-0000-000000000001'");
            assertIntegrityViolation(connection, "INSERT INTO m_role (code, name) VALUES ('USER', 'Duplicate')");
            assertIntegrityViolation(connection, """
                    INSERT INTO m_user (email, password_hash, full_name, role_id)
                    SELECT 'user1@erhms.local', 'duplicate-hash', 'Duplicate', role_id
                    FROM m_role WHERE code = 'USER'
                    """);
            assertIntegrityViolation(connection, "UPDATE recruitment_requests SET quantity = 0");
            assertIntegrityViolation(connection, "UPDATE recruitment_requests SET quantity = 10001");
            assertIntegrityViolation(connection, "UPDATE recruitment_requests SET shift = 'INVALID'");
            assertIntegrityViolation(connection, "UPDATE recruitment_requests SET status = 'INVALID'");
        }
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

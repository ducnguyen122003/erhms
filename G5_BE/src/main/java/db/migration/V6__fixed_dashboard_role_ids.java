package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Upgrades only the known V1/V5 role layout; unexpected role IDs remain invalid. */
public class V6__fixed_dashboard_role_ids extends BaseJavaMigration {
    private static final List<String> CODES = List.of(
            "ADMIN", "LECTURER", "STUDENT", "ACCOUNTANT", "IT_SUPPORT", "MANAGER", "HR");
    private static final List<String> NAMES = List.of(
            "Quản trị viên", "Giảng viên", "Học viên", "Kế toán", "Hỗ trợ thiết bị",
            "Quản lý học viên", "Tuyển sinh và tuyển dụng");
    private static final Map<String, String> LEGACY_CODES = Map.of(
            "USER", "HR", "EMPLOYER_HR", "HR", "SERVICE_MANAGER", "MANAGER");

    @Override
    public void migrate(Context context) throws Exception {
        Connection connection = context.getConnection();
        if (connection.getMetaData().getDatabaseProductName().equals("PostgreSQL")) {
            // Prevent concurrent inserts from taking IDs or gaining permissions during the remap.
            execute(connection, "LOCK TABLE m_role, m_user IN EXCLUSIVE MODE");
        }
        List<ExistingRole> roles = new ArrayList<>();
        try (var statement = connection.createStatement();
             var result = statement.executeQuery("SELECT role_id, code, name FROM m_role ORDER BY role_id")) {
            while (result.next()) {
                roles.add(new ExistingRole(result.getLong(1), result.getString(2), result.getString(3)));
            }
        }
        boolean historicalLayout = hasRole(roles, 1, "USER") && hasRole(roles, 2, "SERVICE_MANAGER")
                && CODES.stream().allMatch(code -> hasRole(roles, CODES.indexOf(code) + 3L, code));
        // A manually configured database is authoritative. Do not turn an unsupported
        // assignment (for example ADMIN at ID 8) into a valid privilege on restart.
        if (!historicalLayout) return;

        long nextId = Math.max(7L, roles.stream().mapToLong(ExistingRole::id).max().orElse(0L));
        List<StagedRole> staged = new ArrayList<>();
        for (ExistingRole role : roles) {
            String canonicalCode = LEGACY_CODES.getOrDefault(role.code(), role.code());
            if (!CODES.contains(canonicalCode)) continue;
            nextId = Math.addExact(nextId, 1);
            execute(connection, "INSERT INTO m_role (role_id, code, name) VALUES (?, ?, ?)",
                    nextId, "__V6_" + UUID.randomUUID().toString().replace("-", ""), role.name());
            execute(connection, "UPDATE m_user SET role_id = ? WHERE role_id = ?", nextId, role.id());
            execute(connection, "DELETE FROM m_role WHERE role_id = ?", role.id());
            staged.add(new StagedRole(nextId, CODES.indexOf(canonicalCode) + 1L));
        }
        for (int index = 0; index < CODES.size(); index++) {
            String code = CODES.get(index);
            String name = roles.stream().filter(role -> code.equals(role.code()))
                    .map(ExistingRole::name).findFirst().orElse(NAMES.get(index));
            execute(connection, "INSERT INTO m_role (role_id, code, name) VALUES (?, ?, ?)", index + 1L, code, name);
        }
        for (StagedRole role : staged) {
            execute(connection, "UPDATE m_user SET role_id = ? WHERE role_id = ?", role.canonicalId(), role.id());
            execute(connection, "DELETE FROM m_role WHERE role_id = ?", role.id());
        }
        // Explicit IDs do not advance the sequence on either PostgreSQL or H2.
        execute(connection, "ALTER SEQUENCE m_role_seq RESTART WITH " + Math.addExact(nextId, 1));
    }

    private static boolean hasRole(List<ExistingRole> roles, long id, String code) {
        return roles.stream().anyMatch(role -> role.id() == id && code.equals(role.code()));
    }

    private static void execute(Connection connection, String sql, Object... values) throws SQLException {
        try (var statement = connection.prepareStatement(sql)) {
            for (int index = 0; index < values.length; index++) statement.setObject(index + 1, values[index]);
            statement.executeUpdate();
        }
    }

    private record ExistingRole(long id, String code, String name) { }
    private record StagedRole(long id, long canonicalId) { }
}

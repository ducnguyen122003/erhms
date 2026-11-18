package db.migration;

import java.util.ArrayList;
import java.util.List;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

/** The original inline status check has different generated names in PostgreSQL and H2. */
public class V17__admission_scanned_profiles extends BaseJavaMigration {
    @Override
    public void migrate(Context context) throws Exception {
        var connection = context.getConnection();
        List<String> checks = new ArrayList<>();
        try (var statement = connection.createStatement();
             var result = statement.executeQuery("""
                     SELECT t.constraint_name
                     FROM information_schema.table_constraints t
                     JOIN information_schema.check_constraints c
                       ON c.constraint_schema=t.constraint_schema AND c.constraint_name=t.constraint_name
                     WHERE t.table_schema=CURRENT_SCHEMA() AND LOWER(t.table_name)='consultation_followups'
                       AND t.constraint_type='CHECK' AND LOWER(c.check_clause) LIKE '%contacted%'
                     """)) {
            while (result.next()) checks.add(result.getString(1));
        }
        try (var statement = connection.createStatement()) {
            for (String check : checks)
                statement.execute("ALTER TABLE consultation_followups DROP CONSTRAINT \"" + check.replace("\"", "\"\"") + "\"");
            statement.execute("ALTER TABLE consultation_followups ADD CONSTRAINT ck_consultation_profile_status CHECK (status IN ('NEW','CONTACTED','APPOINTMENT','PROFILE_PENDING','PROFILE_CREATED','CONTRACTED','CLOSED'))");
            statement.execute("ALTER TABLE consultation_followups ADD COLUMN scan_file_id UUID REFERENCES workflow_files(id)");
            statement.execute("ALTER TABLE consultation_followups ADD COLUMN profile_submitted_at TIMESTAMP WITH TIME ZONE");
            statement.execute("ALTER TABLE consultation_followups ADD COLUMN profile_created_at TIMESTAMP WITH TIME ZONE");
            statement.execute("ALTER TABLE consultation_followups ADD COLUMN profile_student_id BIGINT REFERENCES m_user(user_id)");
        }
    }
}

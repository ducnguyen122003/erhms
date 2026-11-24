package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Convert existing paid contracts' reserved seats into enrollments. */
public class V33__enroll_students_with_confirmed_tuition extends BaseJavaMigration {
    private static final String ELIGIBLE_CONTRACTS = """
            WITH eligible AS (
                SELECT c.id AS contract_id, c.code AS contract_code, c.status AS contract_status,
                       c.student_id, c.manager_id, cl.id AS class_id, cl.code AS class_code,
                       cl.name AS class_name, cl.lecturer_id,
                       COUNT(*) OVER (PARTITION BY c.student_id) AS eligible_count
                FROM training_contracts c
                JOIN m_user student ON student.user_id = c.student_id
                JOIN training_classes cl ON cl.id = c.class_id
                JOIN training_courses course ON course.id = cl.course_id
                WHERE c.handed_over_at IS NOT NULL
                  AND (c.status = 'ACTIVE' OR (c.status = 'SIGNED' AND c.tuition_due > 0))
                  AND student.role_id = 3 AND student.account_enabled = TRUE AND student.account_locked = FALSE
                  AND cl.status IN ('PLANNED', 'ACTIVE')
                  AND (c.course_id = course.id OR (c.course_id IS NULL AND c.registered_course_name = course.name))
                  AND COALESCE((SELECT SUM(t.amount) FROM financial_transactions t
                                WHERE t.contract_id = c.id AND t.kind = 'TUITION' AND t.status = 'CONFIRMED'), 0) >= c.tuition_due
                  AND NOT EXISTS (SELECT 1 FROM training_enrollments e WHERE e.contract_id = c.id)
                  AND NOT EXISTS (SELECT 1 FROM training_enrollments e WHERE e.student_id = c.student_id
                                  AND (e.class_id = cl.id OR e.status IN ('ACTIVE', 'PENDING')))
                  AND (SELECT COUNT(*) FROM training_contracts reserved
                       WHERE reserved.class_id = cl.id AND reserved.status <> 'CANCELLED')
                    + (SELECT COUNT(*) FROM training_enrollments occupied
                       WHERE occupied.class_id = cl.id AND occupied.status IN ('ACTIVE', 'PENDING')
                         AND NOT EXISTS (SELECT 1 FROM training_contracts linked WHERE linked.id = occupied.contract_id
                                         AND linked.class_id = cl.id AND linked.status <> 'CANCELLED')) <= cl.capacity
            )
            SELECT * FROM eligible WHERE eligible_count = 1 ORDER BY class_id, student_id
            """;

    @Override
    public void migrate(Context context) throws SQLException {
        Connection connection = context.getConnection();
        // Keep payment confirmation and class edits from racing the one-time snapshot on PostgreSQL.
        if ("PostgreSQL".equals(connection.getMetaData().getDatabaseProductName())) {
            try (var lock = connection.createStatement()) {
                lock.execute("LOCK TABLE training_contracts, financial_transactions, m_user, training_classes, training_enrollments IN EXCLUSIVE MODE");
            }
        }

        Set<UUID> affectedClasses = new HashSet<>();
        try (var enroll = connection.prepareStatement("""
                    INSERT INTO training_enrollments(id,class_id,student_id,contract_id,status,assignment_status)
                    VALUES (?,?,?,?,'ACTIVE',?)
                    """);
             var activate = connection.prepareStatement("UPDATE training_contracts SET status='ACTIVE',version=version+1 WHERE id=?");
             var classVersion = connection.prepareStatement("UPDATE training_classes SET version=version+1 WHERE id=?");
             var notify = connection.prepareStatement("""
                     INSERT INTO user_notifications(id,recipient_id,title,content,target_screen,target_id)
                     VALUES (?,?,'Đã xếp lớp',?,?,?)
                     """)) {
            for (Placement placement : eligible(connection)) {
                enroll.setObject(1,UUID.randomUUID());
                enroll.setObject(2,placement.classId());
                enroll.setLong(3,placement.studentId());
                enroll.setObject(4,placement.contractId());
                enroll.setString(5,placement.hasLecturer()?"CONFIRMED":"PENDING");
                enroll.executeUpdate();
                if ("SIGNED".equals(placement.contractStatus())) {
                    activate.setObject(1,placement.contractId());
                    activate.executeUpdate();
                }
                affectedClasses.add(placement.classId());
                String message = "Học viên đã được xếp vào lớp " + placement.classCode() + " · " + placement.className()
                        + " sau khi xác nhận đủ học phí theo hợp đồng " + placement.contractCode() + ".";
                send(notify,placement.studentId(),message,"TimeTable_Student",null);
                if (placement.managerId()!=null)
                    send(notify,placement.managerId(),message,"ClassDetail",placement.classId().toString());
            }
            for (UUID classId : affectedClasses) {
                classVersion.setObject(1,classId);
                classVersion.executeUpdate();
            }
        }
    }

    static List<Placement> eligible(Connection connection) throws SQLException {
        try (var query = connection.prepareStatement(ELIGIBLE_CONTRACTS);
             var rows = query.executeQuery()) {
            List<Placement> placements = new ArrayList<>();
            while (rows.next()) {
                placements.add(new Placement(rows.getObject("contract_id",UUID.class),rows.getString("contract_code"),
                        rows.getString("contract_status"),rows.getLong("student_id"),rows.getObject("manager_id",Long.class),
                        rows.getObject("class_id",UUID.class),rows.getString("class_code"),rows.getString("class_name"),
                        rows.getObject("lecturer_id")!=null));
            }
            return placements;
        }
    }

    private void send(java.sql.PreparedStatement notify,long recipient,String message,String screen,String id) throws SQLException {
        notify.setObject(1,UUID.randomUUID());
        notify.setLong(2,recipient);
        notify.setString(3,message);
        notify.setString(4,screen);
        notify.setString(5,id);
        notify.executeUpdate();
    }

    record Placement(UUID contractId,String contractCode,String contractStatus,long studentId,Long managerId,
                     UUID classId,String classCode,String className,boolean hasLecturer) {}
}

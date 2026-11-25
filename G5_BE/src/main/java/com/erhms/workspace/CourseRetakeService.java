package com.erhms.workspace;

import com.erhms.user.entity.User;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.erhms.workspace.WorkflowData.*;

@Service
public class CourseRetakeService {
    private static final int RETAKE_PERCENT = 70;
    private static final String OFFER_FIELDS = """
            SELECT o.id,o.student_id,o.original_tuition,o.retake_tuition,o.source_contract_id AS original_contract_id,
                   o.new_contract_id AS retake_contract_id,o.offered_at,o.registered_at,e.class_id AS original_class_id,
                   c.code AS contract_code,c.deposit_due,c.terms,
                   COALESCE(c.registered_course_name,co.name,cl.name) AS course_name
            FROM course_retake_offers o JOIN training_enrollments e ON e.id=o.enrollment_id
            JOIN training_contracts c ON c.id=o.source_contract_id JOIN training_classes cl ON cl.id=e.class_id
            LEFT JOIN training_courses co ON co.id=cl.course_id
            """;

    private final WorkflowData data;
    private final TrainingWorkflows training;
    private final RetakeContractService contracts;

    public CourseRetakeService(WorkflowData data, TrainingWorkflows training, RetakeContractService contracts) {
        this.data = data;
        this.training = training;
        this.contracts = contracts;
    }

    // Callers hold the original contract lock while confirming the result or returning a linked loan.
    void offerIfReadyForEnrollment(UUID enrollmentId, User actor) {
        var candidates = data.rows("""
                SELECT e.contract_id,e.student_id,
                       COALESCE(c.registered_course_name,co.name,cl.name) AS course_name
                FROM training_enrollments e JOIN training_contracts c ON c.id=e.contract_id
                JOIN training_classes cl ON cl.id=e.class_id LEFT JOIN training_courses co ON co.id=cl.course_id
                WHERE e.id=? AND e.academic_failed_at IS NOT NULL AND c.student_id=e.student_id
                  AND NOT EXISTS (SELECT 1 FROM course_retake_offers WHERE enrollment_id=e.id)
                  AND NOT EXISTS (SELECT 1 FROM equipment_loans l WHERE l.student_id=e.student_id AND l.returned_at IS NULL
                      AND (l.contract_id=e.contract_id OR (l.contract_id IS NULL AND l.class_id=e.class_id)))
                """, enrollmentId);
        if (candidates.isEmpty()) return;

        var candidate = candidates.get(0);
        UUID source = uuid(candidate.get("contractId").toString());
        BigDecimal original = data.jdbc.queryForObject("""
                SELECT COALESCE((SELECT o.original_tuition FROM course_retake_offers o WHERE o.new_contract_id=c.id),c.tuition_due)
                FROM training_contracts c WHERE c.id=?
                """, BigDecimal.class, source);
        BigDecimal reduced = original.multiply(BigDecimal.valueOf(RETAKE_PERCENT, 2)).setScale(0, RoundingMode.HALF_UP).min(original);
        UUID offer = UUID.randomUUID();
        long student = ((Number) candidate.get("studentId")).longValue();
        data.jdbc.update("""
                INSERT INTO course_retake_offers(id,enrollment_id,source_contract_id,student_id,original_tuition,retake_tuition)
                VALUES (?,?,?,?,?,?)
                """, offer, enrollmentId, source, student, original, reduced);
        training.notify(student, "Đăng ký học lại với 70% học phí",
                "Bạn có thể đăng ký học lại khóa " + candidate.get("courseName") + " với 70% học phí gốc. Mở thông báo để chọn lớp và đăng ký.",
                "StudentRetake", offer.toString(), actor);
    }

    List<Map<String,Object>> failedEnrollmentsForLoan(UUID loanId) {
        return data.rows("""
                SELECT e.id FROM equipment_loans l JOIN training_enrollments e ON e.student_id=l.student_id
                  AND (e.contract_id=l.contract_id OR (l.contract_id IS NULL AND e.class_id=l.class_id))
                WHERE l.id=? AND e.academic_failed_at IS NOT NULL AND e.contract_id IS NOT NULL ORDER BY e.contract_id
                """, loanId);
    }

    public Map<String,Object> view(String id, User student) {
        role(student, "STUDENT");
        if (id == null) {
            return Map.of("resource", "retakes", "canWrite", false, "items", data.rows(
                    OFFER_FIELDS + "WHERE o.student_id=? ORDER BY o.offered_at DESC", student.id));
        }
        var offer = data.one(OFFER_FIELDS + "WHERE o.id=? AND o.student_id=?", uuid(id), student.id);
        offer.put("retakePercent", RETAKE_PERCENT);
        var classes = offer.get("retakeContractId") == null
                ? contracts.classes(sourceContract(offer), uuid(offer.get("originalClassId").toString()), student.id)
                : List.<Map<String,Object>>of();
        return Map.of("resource", "retakes", "record", offer, "related", Map.of("classes", classes),
                "canWrite", offer.get("retakeContractId") == null && !classes.isEmpty());
    }

    public Map<String,Object> register(String id, Map<String,Object> input, User student) {
        role(student, "STUDENT");
        UUID offerId = uuid(id);
        data.one("SELECT id FROM course_retake_offers WHERE id=? AND student_id=? FOR UPDATE", offerId, student.id);
        var offer = data.one(OFFER_FIELDS + "WHERE o.id=? AND o.student_id=?", offerId, student.id);
        if (offer.get("retakeContractId") != null) {
            return Map.of("screen", "TuitionDetail_Student", "id", offer.get("retakeContractId"),
                    "notice", "Bạn đã đăng ký học lại. Mở hợp đồng để nộp học phí.");
        }
        if (!flag(input, "accepted")) throw invalid("accepted", "Xác nhận đồng ý với nội dung hợp đồng học lại trước khi đăng ký");
        UUID contract = contracts.create(sourceContract(offer), uuid(offer.get("originalClassId").toString()),
                uuid(input, "classId"), offerId, (BigDecimal) offer.get("retakeTuition"), student);
        data.jdbc.update("UPDATE course_retake_offers SET new_contract_id=?,registered_at=CURRENT_TIMESTAMP WHERE id=?", contract, offerId);
        return Map.of("screen", "TuitionDetail_Student", "id", contract.toString(),
                "notice", "Đã đăng ký học lại. Bạn có thể xem hợp đồng mới và nộp học phí.");
    }

    private Map<String,Object> sourceContract(Map<String,Object> offer) {
        return data.one("SELECT * FROM training_contracts WHERE id=?", uuid(offer.get("originalContractId").toString()));
    }
}

package com.erhms.workspace;

import com.erhms.user.entity.User;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import static com.erhms.workspace.WorkflowData.*;

@Service
public class RetakeContractService {
    private static final ZoneId LOCAL_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final String ELIGIBLE_CLASSES = """
            FROM training_classes cl JOIN m_user lecturer ON lecturer.user_id=cl.lecturer_id
            WHERE cl.course_id=? AND cl.id<>? AND cl.plan_fixed=TRUE AND cl.starts_on>?
              AND cl.ends_on>=cl.starts_on AND cl.status IN ('PLANNED','ACTIVE')
              AND lecturer.role_id=2 AND lecturer.account_enabled=TRUE AND lecturer.account_locked=FALSE
              AND NOT EXISTS (SELECT 1 FROM training_enrollments e WHERE e.class_id=cl.id AND e.student_id=?)
              AND NOT EXISTS (SELECT 1 FROM training_contracts c WHERE c.class_id=cl.id AND c.student_id=? AND c.status<>'CANCELLED')
            """;

    private final WorkflowData d;
    private final ObjectMapper json;

    public RetakeContractService(WorkflowData data,ObjectMapper json){
        this.d=data;
        this.json=json;
    }

    public List<Map<String,Object>> classes(Map<String,Object> source,UUID originalClass,long studentId){
        UUID course=courseId(source,originalClass);
        if(course==null)return List.of();
        String query="SELECT cl.id,cl.code,cl.name,cl.starts_on,cl.ends_on,cl.weekdays,cl.start_time,cl.end_time,cl.capacity,lecturer.full_name AS lecturer_name "
                +ELIGIBLE_CLASSES+" ORDER BY cl.starts_on,cl.code,cl.id";
        return d.rows(query,course,originalClass,LocalDate.now(LOCAL_ZONE),studentId,studentId)
                .stream().filter(this::hasSeat).toList();
    }

    @Transactional(propagation=Propagation.MANDATORY)
    public UUID create(Map<String,Object> source,UUID originalClass,UUID newClass,UUID offerId,BigDecimal retakeTuition,User student){
        role(student,"STUDENT");
        if(!(source.get("studentId") instanceof Number owner)||owner.longValue()!=student.id)throw missing();
        UUID course=courseId(source,originalClass);
        if(course==null)throw conflict("Hợp đồng cũ chưa liên kết được với khóa học.");
        if(newClass.equals(originalClass))throw invalid("classId","Chọn lớp học lại khác lớp cũ.");

        d.one("SELECT user_id FROM m_user WHERE user_id=? AND role_id=3 AND account_enabled=TRUE AND account_locked=FALSE FOR UPDATE",student.id);
        d.one("SELECT id FROM training_classes WHERE id=? FOR UPDATE",newClass);
        LocalDate today=LocalDate.now(LOCAL_ZONE);
        var available=d.rows("SELECT cl.id,cl.starts_on,cl.ends_on,cl.capacity "+ELIGIBLE_CLASSES+" AND cl.id=?",
                course,originalClass,today,student.id,student.id,newClass);
        if(available.isEmpty()||!hasSeat(available.get(0)))
            throw invalid("classId","Lớp đã hết chỗ hoặc lịch lớp vừa thay đổi. Chọn một lớp học lại còn chỗ.");

        var classroom=available.get(0);
        LocalDate startsOn=LocalDate.parse(classroom.get("startsOn").toString());
        LocalDate endsOn=LocalDate.parse(classroom.get("endsOn").toString());
        UUID id=UUID.randomUUID();
        String code="HD-HL-"+id.toString().substring(0,8).toUpperCase(Locale.ROOT);
        String evidence="Học viên xác nhận điều khoản học lại trực tuyến trên phiếu "+offerId+" của hợp đồng "+source.get("code")+".";

        d.jdbc.update("""
                INSERT INTO training_contracts(id,code,student_id,student_email,applicant_name,applicant_phone,consultation_id,
                    course_id,class_id,registered_course_name,registered_duration_months,tuition_due,deposit_due,status,
                    created_by,manager_id,accountant_id,starts_on,payment_due_on,terms,template_data,signed_on,signature_method,
                    signature_evidence,signature_approved_at,handed_over_at)
                SELECT ?,?,c.student_id,c.student_email,c.applicant_name,c.applicant_phone,c.consultation_id,
                    ?,?,c.registered_course_name,c.registered_duration_months,?,c.deposit_due,'SIGNED',
                    ?,c.manager_id,c.accountant_id,?,?,c.terms,?,?,'RETAKE_ACCEPTANCE',?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP
                FROM training_contracts c WHERE c.id=?
                """,id,code,course,newClass,retakeTuition,student.id,startsOn,startsOn,template(source,today,startsOn,endsOn),today,
                evidence,uuid(source.get("id").toString()));
        return id;
    }

    private UUID courseId(Map<String,Object> contract,UUID originalClass){
        Object course=contract.get("courseId");
        if(course==null)course=d.one("SELECT course_id FROM training_classes WHERE id=?",originalClass).get("courseId");
        return course==null?null:uuid(course.toString());
    }

    private boolean hasSeat(Map<String,Object> classroom){
        UUID classId=uuid(classroom.get("id").toString());
        return ClassReservations.occupied(d,classId,null)<((Number)classroom.get("capacity")).longValue();
    }

    private String template(Map<String,Object> source,LocalDate today,LocalDate startsOn,LocalDate endsOn){
        try{
            Map<String,Object> values=json.readValue(Objects.toString(source.get("templateData"),"{}"),new TypeReference<>(){});
            values.put("agreementDate",today.toString());
            values.put("effectiveOn",today.toString());
            values.put("latestOpeningOn",startsOn.toString());
            values.put("endsOn",endsOn.toString());
            return json.writeValueAsString(values);
        }catch(JsonProcessingException failure){
            throw new IllegalStateException("Không chuẩn bị được mẫu hợp đồng học lại",failure);
        }
    }
}

package com.erhms.workspace;

import com.erhms.admin.service.ScreenPermissionsService;
import com.erhms.user.entity.User;
import com.erhms.user.service.CurrentUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.access.AccessDeniedException;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static com.erhms.workspace.WorkflowData.*;

@Service
public class WorkspaceService {
    final WorkflowData d;final CurrentUser current;final ScreenCatalog catalog;final ScreenPermissionsService permissions;
    final TrainingWorkflows training;final FinanceWorkflows finance;final EquipmentWorkflows equipment;final MasterDataService masters;
    public WorkspaceService(WorkflowData d,CurrentUser current,ScreenCatalog catalog,ScreenPermissionsService permissions,TrainingWorkflows training,FinanceWorkflows finance,EquipmentWorkflows equipment,MasterDataService masters){this.masters=masters;this.d=d;this.current=current;this.catalog=catalog;this.permissions=permissions;this.training=training;this.finance=finance;this.equipment=equipment;}
    User access(String code){var screen=catalog.get(code);User u=current.get();catalog.requireRole(screen,u.getRoleCode());permissions.require(screen.functionCode());return u;}
    public static String resource(String code){return switch(code){
        case "ListAdmissionApplication","ListAdmissionApplicationDetail","AdmissionConsultation"->"admissions";
        case "ListStudent","StudentProfileDetail","EditStudentProfile","StudentProfileDetail_Student"->"students";
        case "ListNotification","ViewNotification","SendNotification"->"notifications";
        case "ListTrainingContract","AddTrainingContract","EditTrainingContract","TrainingContractDetail","ContractSignature"->"contracts";
        case "ListCourse","AddCourse","EditCourse","CourseDetail"->"courses";
        case "ListClass","ClassDetail","EditClass","ListClass_Lecturer","ClassDetail_Lecturer","ClassGradebook"->"classes";
        case "AutoAssignmentReview","AutoAssignmentDetail"->"assignments";
        case "TimeTable_Manager","TimeTable_Lecturer","TimeTable_Student","TimeTable_EditSession","TimeTable_Conflict","ListAttendance","ClassAttendance","Attendance_Correction"->"sessions";
        case "StudentAttendance"->"student-attendance";case "StudentResults"->"grades";
        case "ListEquipmentRequest","AddEquipmentRequest","EquipmentRequestDetail_Lecturer","EquipmentRequestDetail_IT","ListEquipmentRequest_IT","AssetHandover"->"requests";
        case "ListTuitionManagement","TuitionDetail","TuitionDetail_Student","PaymentStatus_Admissions","PaymentInstruction"->"tuition";
        case "ListPayment","PaymentDetail","PaymentDetail_Mismatch","PaymentDetail_Duplicate"->"payments";
        case "DepositList","DepositRefund"->"deposits";
        case "ListAsset","AddAsset","EditAsset","AssetDetail","HistoryAssetAssignment","AssetMaintenance"->"assets";
        case "StudentAssets","StudentAssetReceipt","ListAssetRecovery","ListAssetDetail","Recovery_Withdrawal","AssetHandover_Receipt"->"loans";
        case "ListAssetIncident","AssetIncidentDetail","AssetIncidentDetail_Accountant"->"incidents";
        case "ListTrainingClosure","TrainingClosureDetail","TrainingClosureDetail_Ready","TrainingClosureDetail_Withdrawal","RecordWithdrawal"->"closures";
        default->throw missing();};}
    @Transactional(readOnly=true)
    public Map<String,Object> view(String code,String id,String q,String status,int page,String from,String to){
        User u=access(code);if(code.startsWith("Reports_"))return report(code,u,from,to);
        String resource=resource(code);if(page<0||page>100000||q.length()>200||status.length()>40)throw invalid("filter","Bộ lọc không hợp lệ");
        if(code.equals("StudentProfileDetail_Student"))id=u.id.toString();
        Map<String,Object> result=new LinkedHashMap<>();result.put("resource",resource);result.put("canWrite",canWrite(code,u));
        if(id!=null){var record=d.record(resource,id,u);result.put("record",record);result.put("related",related(code,resource,record,u));}
        else if(!Set.of("AddCourse","AddTrainingContract","AddEquipmentRequest","AddAsset","SendNotification").contains(code)){
            long total;
            if(code.equals("TimeTable_Conflict")){
                String query="SELECT scoped.* FROM ("+d.base(resource,u)+") scoped WHERE LOWER(title) LIKE ? ESCAPE '!' AND EXISTS(SELECT 1 FROM training_classes own WHERE own.id=scoped.class_id AND own.status<>'CANCELLED') AND EXISTS(SELECT 1 FROM training_sessions b JOIN training_classes bc ON bc.id=b.class_id WHERE b.id<>scoped.id AND bc.status<>'CANCELLED' AND b.starts_at<scoped.ends_at AND b.ends_at>scoped.starts_at AND (b.class_id=scoped.class_id OR bc.lecturer_id=scoped.lecturer_id OR (b.classroom_id=scoped.classroom_id OR (b.classroom_id IS NULL AND LOWER(b.room)=LOWER(scoped.room)))))";
                String search="%"+q.toLowerCase(Locale.ROOT).replace("!","!!").replace("%","!%").replace("_","!_")+"%";
                result.put("items",d.rows(query+" ORDER BY starts_at LIMIT 25 OFFSET ?",search,page*25));total=d.count("SELECT COUNT(*) FROM ("+query+") selected",search);
                result.put("stats",List.of(Map.of("label","Buổi có xung đột","value",total),Map.of("label","Lớp bị ảnh hưởng","value",d.count("SELECT COUNT(DISTINCT class_id) FROM ("+query+") selected",search)),Map.of("label","Phòng có xung đột","value",d.count("SELECT COUNT(DISTINCT room) FROM ("+query+") selected",search)),Map.of("label","Giảng viên liên quan","value",d.count("SELECT COUNT(DISTINCT lecturer_id) FROM ("+query+") selected",search))));
            }else if(resource.equals("sessions")&&!from.isBlank()){
                LocalDate begin=date(Map.of("from",from),"from",true),end=to.isBlank()?begin.plusDays(6):date(Map.of("to",to),"to",true);
                if(end.isBefore(begin)||end.isAfter(begin.plusDays(366)))throw invalid("to","Khoảng thời gian không hợp lệ");
                OffsetDateTime start=begin.atStartOfDay(ZoneId.of("Asia/Ho_Chi_Minh")).toOffsetDateTime(),finish=end.plusDays(1).atStartOfDay(ZoneId.of("Asia/Ho_Chi_Minh")).toOffsetDateTime();
                String query="SELECT scoped.* FROM ("+d.base(resource,u)+") scoped WHERE starts_at>=? AND starts_at<? AND LOWER(title) LIKE ? ESCAPE '!'";
                String search="%"+q.toLowerCase(Locale.ROOT).replace("!","!!").replace("%","!%").replace("_","!_")+"%";
                result.put("items",d.rows(query+" ORDER BY starts_at LIMIT 25 OFFSET ?",start,finish,search,page*25));
                total=d.count("SELECT COUNT(*) FROM ("+query+") selected",start,finish,search);
                long finalized=d.count("SELECT COUNT(*) FROM ("+query+") selected WHERE attendance_finalized=TRUE",start,finish,search),classes=d.count("SELECT COUNT(DISTINCT class_id) FROM ("+query+") selected",start,finish,search);
                result.put("stats",List.of(Map.of("label","Buổi học trong kỳ","value",total),Map.of("label","Chưa chốt điểm danh","value",total-finalized),Map.of("label","Đã chốt điểm danh","value",finalized),Map.of("label","Lớp có lịch","value",classes)));
            }else{result.put("items",d.page(resource,u,q,status,page));total=d.total(resource,u,q,status);}
            result.put("total",total);result.put("page",page);result.put("totalPages",(total+24)/25);
            // Counts describe the same scoped/filter result, not the sample numbers from SVG.
            if(!result.containsKey("stats"))result.put("stats",d.statistics(resource,u,q,status));
        }
        result.put("options",options(code,u,id));return result;
    }
    private boolean canWrite(String code,User u){return switch(u.getRoleCode()){
        case "MANAGER"->Set.of("AddCourse","EditCourse","EditClass","TimeTable_Manager","TimeTable_EditSession","TimeTable_Conflict","EditStudentProfile","SendNotification","AutoAssignmentDetail","TrainingClosureDetail","TrainingClosureDetail_Ready","TrainingClosureDetail_Withdrawal","RecordWithdrawal").contains(code);
        case "HR"->Set.of("AdmissionConsultation","AddTrainingContract","EditTrainingContract","ContractSignature").contains(code);
        case "LECTURER"->Set.of("ClassAttendance","Attendance_Correction","ClassGradebook","AddEquipmentRequest").contains(code);
        case "ACCOUNTANT"->Set.of("ListPayment","PaymentDetail","PaymentDetail_Mismatch","PaymentInstruction","TuitionDetail","DepositRefund","AssetIncidentDetail_Accountant").contains(code);
        case "IT_SUPPORT"->Set.of("AddAsset","EditAsset","AssetDetail","AssetMaintenance","EquipmentRequestDetail_IT","AssetHandover","AssetHandover_Receipt","ListAssetDetail","Recovery_Withdrawal","AssetIncidentDetail","ListAssetIncident").contains(code);
        default->false;};}
    Map<String,Object> related(String code,String resource,Map<String,Object> record,User u){
        Map<String,Object> related=new LinkedHashMap<>();String id=record.get("id").toString();UUID key=resource.equals("students")?null:uuid(id);
        switch(resource){
            case "admissions"->related.put("history",d.rows("SELECT n.*,u.full_name AS actor_name FROM consultation_notes n JOIN m_user u ON u.user_id=n.actor_id WHERE n.consultation_id=? ORDER BY n.created_at DESC",key));
            case "students"->{long student=((Number)record.get("id")).longValue();related.put("enrollments",d.rows("SELECT e.id,e.class_id,e.status,c.code AS class_code,c.name AS class_name FROM training_enrollments e JOIN training_classes c ON c.id=e.class_id WHERE e.student_id=? AND "+classScope(u,"c"),student));related.put("contracts",d.rows("SELECT id,code,course_name,status FROM ("+d.base("contracts",u)+") scoped WHERE student_id=?",student));}
            case "courses"->{related.put("assessments",d.rows("SELECT title,weight FROM course_assessments WHERE course_id=? ORDER BY title",key));related.put("lecturers",d.rows("SELECT l.lecturer_id AS id,u.full_name AS name FROM course_lecturers l JOIN m_user u ON u.user_id=l.lecturer_id WHERE l.course_id=?",key));related.put("classes",d.rows("SELECT id,code,name,status FROM training_classes c WHERE c.course_id=? AND "+classScope(u,"c"),key));}
            case "classes"->{related.put("students",d.rows("SELECT e.id AS enrollment_id,e.student_id,u.full_name AS student_name,e.status,e.closed_at,e.assignment_status FROM training_enrollments e JOIN m_user u ON u.user_id=e.student_id WHERE e.class_id=? ORDER BY u.full_name",key));
                related.put("sessions",d.rows("SELECT id,title,starts_at,ends_at,room,attendance_finalized,version FROM training_sessions WHERE class_id=? ORDER BY starts_at",key));
                if(code.equals("ClassGradebook")){related.put("assessments",d.rows("SELECT title,weight FROM course_assessments WHERE course_id=? ORDER BY title",record.get("courseId")==null?null:uuid(record.get("courseId").toString())));related.put("grades",d.rows("SELECT g.* FROM training_grades g JOIN training_enrollments e ON e.id=g.enrollment_id WHERE e.class_id=?",key));}}
            case "sessions"->related.put("attendance",d.rows("SELECT e.student_id,u.full_name AS student_name,a.id,a.status,a.note,a.version FROM training_enrollments e JOIN m_user u ON u.user_id=e.student_id LEFT JOIN training_attendance a ON a.student_id=e.student_id AND a.session_id=? WHERE e.class_id=? AND e.status='ACTIVE'"+(u.getRoleCode().equals("STUDENT")?" AND e.student_id="+u.id:"")+" ORDER BY u.full_name",key,uuid(record.get("classId").toString())));
            case "contracts","tuition","deposits"->{
                related.put("transactions",d.rows("SELECT t.id,t.kind,t.amount,t.status,t.created_at,t.confirmed_at FROM financial_transactions t WHERE t.contract_id=?"+(u.getRoleCode().equals("STUDENT")?" AND t.status='CONFIRMED'":"")+" ORDER BY t.created_at DESC",key));
                related.put("instruction",d.rows("SELECT content,version,updated_at FROM payment_instructions WHERE contract_id=?",key));
                if(code.equals("DepositRefund")){related.put("loans",d.rows("SELECT l.id,a.code AS asset_code,l.return_status,l.return_condition,l.returned_at FROM equipment_loans l JOIN equipment_assets a ON a.id=l.asset_id WHERE l.contract_id=?",key));related.put("incidents",d.rows("SELECT i.id,i.description,i.status,i.approved_amount,i.approval_reference,i.settled_at FROM equipment_incidents i JOIN equipment_loans l ON l.id=i.loan_id WHERE l.contract_id=?",key));related.put("settlements",d.rows("SELECT id,deposit_amount,deduction_amount,refund_amount,recipient,bank_reference,evidence,created_at FROM deposit_settlements WHERE contract_id=?",key));}}
            case "assets"->{related.put("history",d.rows("SELECT l.id,l.student_id,u.full_name AS student_name,l.received_at,l.returned_at,l.return_status FROM equipment_loans l JOIN m_user u ON u.user_id=l.student_id WHERE l.asset_id=? ORDER BY l.received_at DESC",key));related.put("maintenance",d.rows("SELECT m.*,u.full_name AS owner_name FROM asset_maintenance m JOIN m_user u ON u.user_id=m.owner_id WHERE m.asset_id=? ORDER BY m.discovered_on DESC",key));}
            case "requests"->{related.put("loans",d.rows("SELECT l.id,a.code AS asset_code,a.name AS asset_name,l.received_at,l.receipt_evidence,l.return_status FROM equipment_loans l JOIN equipment_assets a ON a.id=l.asset_id WHERE l.request_id=?",key));}
            case "closures"->related.put("conditions",training.closureConditions(record));
            default->{}
        }
        return related;
    }
    List<Map<String,Object>> choices(String resource,User u){return d.rows("SELECT scoped.* FROM ("+d.base(resource,u)+") scoped ORDER BY id LIMIT 1000");}
    Map<String,Object> options(String code,User u,String id){
        Map<String,Object> out=new LinkedHashMap<>();
        switch(code){
            case "AddCourse","EditCourse","EditClass"->{
                if(!code.equals("EditClass"))out.put("professions",masters.choices("professions"));
                if(code.equals("EditClass")&&id!=null&&d.record("classes",id,u).get("courseId")!=null)out.put("lecturers",d.rows("SELECT user_id AS id,full_name AS name FROM m_user WHERE role_id=2 AND account_enabled=TRUE AND account_locked=FALSE AND user_id IN(SELECT lecturer_id FROM course_lecturers WHERE course_id=?) ORDER BY full_name",uuid(d.record("classes",id,u).get("courseId").toString())));
                else out.put("lecturers",d.rows("SELECT user_id AS id,full_name AS name FROM m_user WHERE role_id=2 AND account_enabled=TRUE AND account_locked=FALSE ORDER BY full_name"));
            }
            case "AddTrainingContract","EditTrainingContract"->{out.put("admissions",choices("admissions",u));out.put("courses",choices("courses",u));out.put("students",d.rows("SELECT DISTINCT s.user_id AS id,s.full_name AS name,s.email FROM m_user s JOIN consultation_registrations r ON LOWER(r.email)=LOWER(s.email) JOIN consultation_followups f ON f.consultation_id=r.id WHERE s.role_id=3 AND (f.assigned_to=? OR f.assigned_to IS NULL) AND f.status NOT IN ('NEW','CLOSED')",u.id));}
            case "ContractSignature"->out.put("accountants",d.rows("SELECT user_id AS id,full_name AS name FROM m_user WHERE role_id=4 AND account_enabled=TRUE AND account_locked=FALSE ORDER BY full_name"));
            case "TimeTable_EditSession","TimeTable_Manager","TimeTable_Conflict"->{out.put("classes",choices("classes",u));out.put("classrooms",masters.choices("classrooms"));}
            case "AddEquipmentRequest"->{out.put("equipmentGroups",masters.choices("equipmentGroups"));out.put("classes",choices("classes",u));out.put("students",d.rows("SELECT DISTINCT s.user_id AS id,s.full_name AS name,e.class_id FROM training_enrollments e JOIN m_user s ON s.user_id=e.student_id JOIN training_classes c ON c.id=e.class_id WHERE e.status='ACTIVE' AND "+classScope(u,"c")));}
            case "AssetHandover"->out.put("assets",d.rows("SELECT id,code,name FROM equipment_assets WHERE status='AVAILABLE' ORDER BY code"));
            case "AssetMaintenance"->{out.put("assets",choices("assets",u));out.put("itUsers",d.rows("SELECT user_id AS id,full_name AS name FROM m_user WHERE role_id=5 AND account_enabled=TRUE AND account_locked=FALSE ORDER BY full_name"));}
            case "SendNotification"->out.put("students",choices("students",u));
            case "ListPayment"->{out.put("contracts",choices("contracts",u));out.put("banks",masters.choices("banks"));}
            case "AddAsset","EditAsset"->{out.put("equipmentGroups",masters.choices("equipmentGroups"));out.put("storageLocations",masters.choices("storageLocations"));}
            case "ListAssetIncident","AssetIncidentDetail"->{out.put("assets",choices("assets",u));out.put("loans",choices("loans",u));}
            default->{}
        }
        return out;
    }
    @Transactional
    public Map<String,Object> save(String code,String id,Map<String,Object> p){
        User u=access(code);
        return switch(code){
            case "AddCourse"->training.course(null,p,u);case "EditCourse"->training.course(required(id),p,u);
            case "AdmissionConsultation"->training.consultation(required(id),p,u);
            case "EditStudentProfile"->training.student(required(id),p,u);
            case "AddTrainingContract"->training.contract(null,p,u);case "EditTrainingContract"->training.contract(required(id),p,u);case "ContractSignature"->training.signature(required(id),p,u);
            case "EditClass"->training.classroom(required(id),p,u);
            case "TimeTable_EditSession"->training.session(id,p,u);
            case "Attendance_Correction"->training.attendance(required(id),p,u,true);
            case "AddEquipmentRequest"->equipment.request(p,u);
            case "AddAsset"->equipment.asset(null,p,u);case "EditAsset"->equipment.asset(required(id),p,u);
            case "AssetMaintenance"->equipment.maintenance(id,p,u);
            case "AssetHandover"->equipment.handover(required(id),p,u);
            case "PaymentInstruction"->finance.instruction(required(id),p,u);
            case "DepositRefund"->finance.settlement(required(id),p,u);
            case "RecordWithdrawal"->training.close(required(id),p,u,"withdraw");
            case "SendNotification"->training.sendNotification(p,u);
            default->throw new AccessDeniedException("Màn này không cho phép gửi biểu mẫu");
        };
    }
    @Transactional
    public Map<String,Object> action(String code,String id,String action,Map<String,Object> p){
        User u=access(code);String requiredId=required(id);
        return switch(code+":"+action){
            case "AutoAssignmentDetail:confirm"->training.assignment(requiredId,p,u);
            case "ClassAttendance:save"->training.attendance(requiredId,p,u,false);
            case "ClassGradebook:save"->training.grades(requiredId,p,u,false);
            case "ClassGradebook:publish"->training.grades(requiredId,p,u,true);
            case "EquipmentRequestDetail_IT:prepare","EquipmentRequestDetail_IT:ready","EquipmentRequestDetail_IT:reject"->equipment.requestState(requiredId,p,u,action);
            case "AssetDetail:complete-maintenance"->equipment.completeMaintenance(requiredId,p,u);
            case "ListAssetDetail:confirm","Recovery_Withdrawal:confirm"->equipment.recover(requiredId,p,u);
            case "ListAssetIncident:create"->equipment.incident(null,p,u);
            case "AssetIncidentDetail:save"->equipment.incident(requiredId,p,u);
            case "AssetIncidentDetail_Accountant:settle"->finance.incident(requiredId,p,u);
            case "ListPayment:import"->finance.importPayment(p,u);
            case "PaymentDetail:confirm","PaymentDetail:mismatch","PaymentDetail_Mismatch:resolve"->finance.payment(requiredId,p,u,action);
            case "TuitionDetail:activate-zero"->finance.activateZeroTuition(requiredId,p,u);
            case "TrainingClosureDetail:academic"->training.close(requiredId,p,u,action);
            case "TrainingClosureDetail_Ready:close","TrainingClosureDetail_Withdrawal:close"->training.close(requiredId,p,u,action);
            case "ViewNotification:read","ListNotification:read"->{UUID key=uuid(requiredId);d.record("notifications",requiredId,u);d.jdbc.update("UPDATE user_notifications SET read_at=COALESCE(read_at,CURRENT_TIMESTAMP) WHERE id=? AND recipient_id=?",key,u.id);yield TrainingWorkflows.saved("ViewNotification",key);}
            default->throw new AccessDeniedException("Không có quyền thực hiện thao tác này");
        };
    }
    String required(String id){if(id==null||id.isBlank())throw invalid("id","Chọn bản ghi trước khi thực hiện");return id;}
    Map<String,Object> report(String code,User u,String from,String to){
        LocalDate begin=from.isBlank()?LocalDate.now().withDayOfMonth(1):date(Map.of("from",from),"from",true),end=to.isBlank()?LocalDate.now():date(Map.of("to",to),"to",true);
        if(end.isBefore(begin))throw invalid("to","Ngày kết thúc phải sau ngày bắt đầu");OffsetDateTime start=begin.atStartOfDay(ZoneId.of("Asia/Ho_Chi_Minh")).toOffsetDateTime(),finish=end.plusDays(1).atStartOfDay(ZoneId.of("Asia/Ho_Chi_Minh")).toOffsetDateTime();
        List<Map<String,Object>> rows;String source=switch(u.getRoleCode()){case "ACCOUNTANT"->"payments";case "IT_SUPPORT"->"loans";case "MANAGER","LECTURER"->"classes";case "HR"->"admissions";default->"";};
        if(source.isEmpty())rows=d.rows("SELECT action_label AS name,result AS status,COUNT(*) AS count FROM system_audit_log WHERE occurred_at>=? AND occurred_at<? GROUP BY action_label,result ORDER BY action_label",start,finish);
        else if(source.equals("payments"))rows=d.rows("SELECT kind AS name,status,COUNT(*) AS count,SUM(amount) AS amount FROM ("+d.base(source,u)+") scoped WHERE created_at>=? AND created_at<? GROUP BY kind,status ORDER BY kind,status",start,finish);
        else if(source.equals("admissions"))rows=d.rows("SELECT program_name AS name,status,COUNT(*) AS count FROM ("+d.base(source,u)+") scoped WHERE created_date>=? AND created_date<? GROUP BY program_name,status ORDER BY program_name",start,finish);
        else if(source.equals("loans"))rows=d.rows("SELECT asset_name AS name,return_status AS status,COUNT(*) AS count FROM ("+d.base(source,u)+") scoped WHERE received_at>=? AND received_at<? GROUP BY asset_name,return_status ORDER BY asset_name",start,finish);
        else rows=d.rows("SELECT name,status,COUNT(*) AS count,SUM(student_count) AS students FROM ("+d.base(source,u)+") scoped WHERE (starts_on IS NULL OR starts_on<=?) AND (ends_on IS NULL OR ends_on>=?) GROUP BY name,status ORDER BY name",end,begin);
        return Map.of("items",rows,"from",begin.toString(),"to",end.toString(),"total",rows.size(),"report",true,"canWrite",false);
    }
}

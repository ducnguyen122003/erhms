package com.erhms.workspace;

import com.erhms.admin.service.ScreenPermissionsService;
import com.erhms.user.entity.User;
import com.erhms.user.StudentCode;
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
    final TrainingWorkflows training;final FinanceWorkflows finance;final EquipmentWorkflows equipment;final MasterDataService masters;final AdmissionProfiles profiles;
    final AdmissionDashboards admissionDashboards;final AdmissionForwarding admissionForwarding;final ContractTemplateData contractTemplate;final ContractSigningWorkflow signing;
    final AdminClassPlanningService classPlanning;final CourseRetakeService retakes;
    public WorkspaceService(WorkflowData d,CurrentUser current,ScreenCatalog catalog,ScreenPermissionsService permissions,TrainingWorkflows training,FinanceWorkflows finance,EquipmentWorkflows equipment,MasterDataService masters,AdmissionProfiles profiles,AdmissionDashboards admissionDashboards,AdmissionForwarding admissionForwarding,ContractTemplateData contractTemplate,ContractSigningWorkflow signing,AdminClassPlanningService classPlanning,CourseRetakeService retakes){this.retakes=retakes;this.classPlanning=classPlanning;this.signing=signing;this.contractTemplate=contractTemplate;this.admissionForwarding=admissionForwarding;this.admissionDashboards=admissionDashboards;this.profiles=profiles;this.masters=masters;this.d=d;this.current=current;this.catalog=catalog;this.permissions=permissions;this.training=training;this.finance=finance;this.equipment=equipment;}
    User access(String code){var screen=catalog.get(code);User u=current.get();catalog.requireRole(screen,u.getRoleCode());permissions.require(screen.functionCode());return u;}
    private String pdfScreen(User actor){return switch(actor.getRoleCode()){
        case "MANAGER"->permissions.codes(actor).contains("TrainingContractDetail")?"TrainingContractDetail":"ContractSignature";
        case "ACCOUNTANT"->"TuitionDetail";
        case "STUDENT"->"TuitionDetail_Student";
        case "HR"->"PaymentStatus_Admissions";
        default->null;
    };}
    @Transactional(readOnly=true)
    public Map<String,Object> contractPdfValues(String id){
        User actor=current.get();var record=pdfRecord(id,actor);
        var values=contractTemplate.values(record,actor);contractTemplate.requireComplete(values);
        values.put("additionalTerms",Objects.toString(record.get("terms"),""));return values;
    }
    private Map<String,Object> pdfRecord(String id,User actor){
        if(actor.getRoleCode().equals("HR")&&permissions.codes(actor).contains("AdmissionContractSigning")
                &&d.count("SELECT COUNT(*) FROM training_contracts WHERE id=? AND signature_admissions_id=? AND signature_sent_at IS NOT NULL",uuid(id),actor.id)>0){
            access("AdmissionContractSigning");return signing.admissionRecord(id,actor);
        }
        String screen=pdfScreen(actor);
        if(screen==null)throw new AccessDeniedException("Bạn không có quyền xuất hợp đồng đào tạo.");
        access(screen);return d.record("contracts",id,actor);
    }
    @Transactional(readOnly=true)
    public Map<String,Object> contractDocument(String id){
        User actor=current.get();var record=pdfRecord(id,actor);
        Map<String,Object> out=new LinkedHashMap<>();out.put("code",record.get("code"));
        if(record.get("printFileId")!=null)out.put("pdf",d.jdbc.queryForObject("SELECT file_data FROM workflow_files WHERE id=?",byte[].class,uuid(record.get("printFileId").toString())));
        else{var values=contractTemplate.values(record,actor);contractTemplate.requireComplete(values);values.put("additionalTerms",Objects.toString(record.get("terms"),""));out.put("values",values);}
        return out;
    }
    public static String resource(String code){return switch(code){
        case "ListAdmissionApplication","ListAdmissionEnrollment","ListAdmissionApplicationDetail","AdmissionConsultation"->"admissions";
        case "ListStudent","StudentProfileDetail","EditStudentProfile","StudentProfileDetail_Student"->"students";
        case "ListNotification","ViewNotification","SendNotification"->"notifications";
        case "ListTrainingContract","AddTrainingContract","EditTrainingContract","TrainingContractDetail","ContractSignature"->"contracts";
        case "ListCourse","AddCourse","EditCourse","CourseDetail"->"courses";
        case "CourseSchedules","ListClass","AddClass","ClassDetail","EditClass","ListClass_Lecturer","ClassDetail_Lecturer","ClassGradebook"->"classes";
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
    public Map<String,Object> view(String code,String id,String q,String status,int page,String from,String to,String tab){
        return view(code,id,q,status,page,from,to,tab,null);
    }
    @Transactional(readOnly=true)
    public Map<String,Object> view(String code,String id,String q,String status,int page,String from,String to,String tab,Integer year){
        return view(code,id,q,status,page,from,to,tab,year,"");
    }
    @Transactional(readOnly=true)
    public Map<String,Object> view(String code,String id,String q,String status,int page,String from,String to,String tab,Integer year,String courseId){
        User u=access(code);if(code.startsWith("Reports_"))return report(code,u,from,to);
        if(code.equals("AdmissionContractSigning"))return signing.view(id,q,status,page,u);
        if(code.equals("StudentRetake"))return retakes.view(id,u);
        String resource=resource(code);if(page<0||page>100000||q.length()>200||status.length()>40)throw invalid("filter","Bộ lọc không hợp lệ");
        if(code.equals("CourseSchedules")){if(id!=null)throw missing();return courseSchedules(u,q,status,page,year,courseId);}
        if(Set.of("ListAdmissionApplication","ListAdmissionEnrollment").contains(code)){
            if(id!=null)throw missing();
            return admissionDashboards.list(code,u,q,status,page,tab,permissions.codes(u).contains("AdmissionConsultation"));
        }
        if(code.equals("StudentProfileDetail_Student"))id=u.id.toString();
        Map<String,Object> result=new LinkedHashMap<>();result.put("resource",resource);result.put("canWrite",canWrite(code,u));
        if(code.equals("ListAdmissionApplication"))result.put("canImport",u.getRoleCode().equals("HR"));
        if(id!=null){var record=d.record(resource,id,u);result.put("record",record);result.put("related",related(code,resource,record,u));
            if(code.equals("ContractSignature"))result.put("canWrite","SIGNATURE_REVIEW".equals(record.get("status"))&&record.get("signedScanFileId")!=null);
            if(Set.of("TrainingContractDetail","ContractSignature").contains(code)&&u.getRoleCode().equals("MANAGER")&&record.get("consultationId")!=null){var assigned=d.rows("SELECT assigned_to FROM consultation_followups WHERE consultation_id=?",uuid(record.get("consultationId").toString()));record.put("defaultAdmissionsId",assigned.isEmpty()?null:assigned.get(0).get("assignedTo"));}
            if(Set.of("contracts","tuition","deposits").contains(resource)){contractTemplate.attachRecord(record,u);result.put("canExportContract",pdfScreen(u)!=null&&permissions.codes(u).contains(pdfScreen(u)));}
            if(resource.equals("admissions")){
                boolean canConsult=u.getRoleCode().equals("HR")&&!"NOT_PROCEEDING".equals(record.get("status"))&&!AdmissionProfiles.submitted(record)&&permissions.codes(u).contains("AdmissionConsultation");
                result.put("canConsult",canConsult);if(code.equals("AdmissionConsultation"))result.put("canWrite",canConsult);
            }
            if(code.equals("ListAdmissionApplicationDetail")){
                boolean canUpload=u.getRoleCode().equals("HR")&&permissions.codes(u).contains("AdmissionConsultation");
                result.put("canSubmitProfile",canUpload&&"DOCUMENT_APPOINTMENT".equals(record.get("status"))&&record.get("documentDueOn")!=null);
                result.put("canEditProfile",canUpload&&AdmissionProfiles.submitted(record));
                result.put("canEditCourse",canUpload&&record.get("forwardedAt")==null);
            }}
        else if(!Set.of("AddCourse","AddClass","AddTrainingContract","AddEquipmentRequest","AddAsset","SendNotification").contains(code)){
            long total;
            if(resource.equals("classes")&&year!=null){
                if(year<1900||year>9999)throw invalid("year","Năm kế hoạch không hợp lệ");
                String query="SELECT scoped.* FROM ("+d.base("classes",u)+") scoped WHERE plan_year=? AND LOWER(name||' '||code) LIKE ? ESCAPE '!'"+(status.isEmpty()?"":" AND status=?");
                List<Object> args=new ArrayList<>(List.of(year,"%"+q.toLowerCase(Locale.ROOT).replace("!","!!").replace("%","!%").replace("_","!_")+"%"));if(!status.isEmpty())args.add(status);
                total=d.count("SELECT COUNT(*) FROM ("+query+") selected",args.toArray());
                long pending=d.count("SELECT COUNT(*) FROM ("+query+") selected WHERE status='PLANNED'",args.toArray()),active=d.count("SELECT COUNT(*) FROM ("+query+") selected WHERE status='ACTIVE'",args.toArray());
                result.put("stats",List.of(Map.of("label","Tổng lớp","value",total),Map.of("label","Chờ khai giảng","value",pending),Map.of("label","Đang học","value",active),Map.of("label","Đã kết thúc","value",total-pending-active)));
                args.add(page*25);result.put("items",d.rows(query+" ORDER BY starts_on,code LIMIT 25 OFFSET ?",args.toArray()));result.put("year",year);
            }else if(code.equals("TimeTable_Conflict")){
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
        case "ADMIN"->Set.of("CourseSchedules","AddClass","EditClass","TimeTable_EditSession").contains(code);
        case "MANAGER"->Set.of("AddTrainingContract","EditTrainingContract","ContractSignature","ClassDetail","EditStudentProfile","SendNotification","AutoAssignmentDetail","TrainingClosureDetail","TrainingClosureDetail_Ready","TrainingClosureDetail_Withdrawal","RecordWithdrawal").contains(code);
        case "HR"->code.equals("AdmissionConsultation");
        case "LECTURER"->Set.of("ClassAttendance","Attendance_Correction","ClassGradebook","AddEquipmentRequest").contains(code);
        case "ACCOUNTANT"->Set.of("ListPayment","PaymentDetail","PaymentDetail_Mismatch","PaymentInstruction","TuitionDetail","DepositRefund","AssetIncidentDetail_Accountant").contains(code);
        case "IT_SUPPORT"->Set.of("AddAsset","EditAsset","AssetDetail","AssetMaintenance","EquipmentRequestDetail_IT","AssetHandover","AssetHandover_Receipt","ListAssetDetail","Recovery_Withdrawal","AssetIncidentDetail","ListAssetIncident").contains(code);
        default->false;};}
    Map<String,Object> related(String code,String resource,Map<String,Object> record,User u){
        Map<String,Object> related=new LinkedHashMap<>();String id=record.get("id").toString();UUID key=resource.equals("students")?null:uuid(id);
        switch(resource){
            case "admissions"->{related.put("documents",profiles.documents(key));related.put("history",d.rows("SELECT n.id,n.outcome,n.channel,n.content,n.appointment_at,n.document_due_on,n.created_at,u.full_name AS actor_name,n.discontinuation_reason FROM consultation_notes n JOIN m_user u ON u.user_id=n.actor_id WHERE n.consultation_id=? ORDER BY n.created_at DESC,n.id",key));}
            case "students"->{long student=((Number)record.get("id")).longValue();related.put("enrollments",d.rows("SELECT e.id,e.class_id,e.status,c.code AS class_code,c.name AS class_name FROM training_enrollments e JOIN training_classes c ON c.id=e.class_id WHERE e.student_id=? AND "+learnerClassScope(u,"c","e.student_id","e.contract_id"),student));related.put("contracts",d.rows("SELECT id,code,course_name,status FROM ("+d.base("contracts",u)+") scoped WHERE student_id=?",student));}
            case "courses"->{related.put("assessments",d.rows("SELECT title,weight FROM course_assessments WHERE course_id=? ORDER BY title",key));related.put("lecturers",d.rows("SELECT l.lecturer_id AS id,u.full_name AS name FROM course_lecturers l JOIN m_user u ON u.user_id=l.lecturer_id WHERE l.course_id=?",key));related.put("classes",d.rows("SELECT id,code,name,status FROM training_classes c WHERE c.course_id=? AND "+(u.getRoleCode().equals("ADMIN")?"1=1":classScheduleScope(u,"c")),key));}
            case "classes"->{related.put("students",d.rows("SELECT e.id AS enrollment_id,e.student_id,"+StudentCode.sql("e.student_id")+" AS student_code,u.full_name AS student_name,e.status,e.closed_at,e.academic_completed_at,e.academic_failed_at,e.assignment_status FROM training_enrollments e JOIN m_user u ON u.user_id=e.student_id JOIN training_classes cl ON cl.id=e.class_id WHERE e.class_id=? AND "+learnerClassScope(u,"cl","e.student_id","e.contract_id")+" ORDER BY u.full_name",key));
                if(code.equals("ClassDetail")&&u.getRoleCode().equals("MANAGER"))related.put("unassignedStudents",training.unassignedStudents(id,u));
                related.put("sessions",d.rows("SELECT id,title,starts_at,ends_at,room,attendance_finalized,version FROM training_sessions WHERE class_id=? ORDER BY starts_at",key));
                if(code.equals("ClassGradebook")){
                    related.put("assessments",d.rows("""
                            SELECT title,weight FROM course_assessments WHERE course_id=?
                            ORDER BY CASE title WHEN 'Điểm kiểm tra lần 1' THEN 1 WHEN 'Điểm kiểm tra lần 2' THEN 2
                                                WHEN 'Điểm kiểm tra cuối kỳ' THEN 3 ELSE 4 END,title
                            """,record.get("courseId")==null?null:uuid(record.get("courseId").toString())));
                    related.put("grades",d.rows("SELECT g.* FROM training_grades g JOIN training_enrollments e ON e.id=g.enrollment_id JOIN training_classes cl ON cl.id=e.class_id WHERE e.class_id=? AND "+learnerClassScope(u,"cl","e.student_id","e.contract_id"),key));
                }}
            case "sessions"->related.put("attendance",d.rows("SELECT e.student_id,"+StudentCode.sql("e.student_id")+" AS student_code,u.full_name AS student_name,a.id,a.status,a.note,a.version FROM training_enrollments e JOIN m_user u ON u.user_id=e.student_id JOIN training_classes cl ON cl.id=e.class_id LEFT JOIN training_attendance a ON a.student_id=e.student_id AND a.session_id=? WHERE e.class_id=? AND e.status='ACTIVE'"+(u.getRoleCode().equals("STUDENT")?" AND e.student_id="+u.id:"")+" AND "+learnerClassScope(u,"cl","e.student_id","e.contract_id")+" ORDER BY u.full_name",key,uuid(record.get("classId").toString())));
            case "contracts","tuition","deposits"->{
                if(u.getRoleCode().equals("MANAGER"))related.put("signingHistory",signing.history(key));
                if(u.getRoleCode().equals("MANAGER")&&record.get("consultationId")!=null)related.put("documents",profiles.documents(uuid(record.get("consultationId").toString())));
                related.put("transactions",d.rows("SELECT t.id,t.kind,t.amount,t.status,t.created_at,t.confirmed_at FROM financial_transactions t WHERE t.contract_id=?"+(u.getRoleCode().equals("STUDENT")?" AND t.status='CONFIRMED'":"")+" ORDER BY t.created_at DESC",key));
                related.put("instruction",d.rows("SELECT content,version,updated_at FROM payment_instructions WHERE contract_id=?",key));
                if(code.equals("DepositRefund")&&u.getRoleCode().equals("ACCOUNTANT")){
                    related.put("loans",d.rows("SELECT l.id,a.code AS asset_code,l.return_status,l.return_condition,l.returned_at FROM equipment_loans l JOIN equipment_assets a ON a.id=l.asset_id WHERE l.contract_id=?",key));
                    related.put("incidents",d.rows("SELECT i.id,i.description,i.status,i.approved_amount,i.approval_reference,i.settled_at FROM equipment_incidents i JOIN equipment_loans l ON l.id=i.loan_id WHERE l.contract_id=?",key));
                    related.put("settlements",d.rows("SELECT id,deposit_amount,deduction_amount,refund_amount,recipient,bank_reference,evidence,created_at FROM deposit_settlements WHERE contract_id=?",key));
                    finance.attachRefundReview(related,record);
                }}
            case "assets"->{related.put("history",d.rows("SELECT l.id,l.student_id,u.full_name AS student_name,l.received_at,l.returned_at,l.return_status FROM equipment_loans l JOIN m_user u ON u.user_id=l.student_id WHERE l.asset_id=? ORDER BY l.received_at DESC",key));related.put("maintenance",d.rows("SELECT m.*,u.full_name AS owner_name FROM asset_maintenance m JOIN m_user u ON u.user_id=m.owner_id WHERE m.asset_id=? ORDER BY m.discovered_on DESC",key));}
            case "requests"->{related.put("loans",d.rows("SELECT l.id,a.code AS asset_code,a.name AS asset_name,l.received_at,l.receipt_evidence,l.return_status FROM equipment_loans l JOIN equipment_assets a ON a.id=l.asset_id WHERE l.request_id=?",key));}
            case "loans"->{
                if(u.getRoleCode().equals("IT_SUPPORT")&&Set.of("ListAssetDetail","Recovery_Withdrawal").contains(code)&&record.get("contractId")!=null){
                    finance.attachRefundReview(related,d.one("SELECT id,status,student_id,version FROM training_contracts WHERE id=?",uuid(record.get("contractId").toString())));
                }}
            case "closures"->related.put("conditions",training.closureConditions(record,u));
            default->{}
        }
        return related;
    }
    List<Map<String,Object>> choices(String resource,User u){return d.rows("SELECT scoped.* FROM ("+d.base(resource,u)+") scoped ORDER BY id LIMIT 1000");}
    Map<String,Object> options(String code,User u,String id){
        Map<String,Object> out=new LinkedHashMap<>();
        if(resource(code).equals("classes")){SortedSet<Integer> years=new TreeSet<>(Comparator.reverseOrder());years.add(LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")).getYear());for(var row:d.rows("SELECT DISTINCT plan_year FROM ("+d.base("classes",u)+") scoped WHERE plan_year IS NOT NULL"))years.add(((Number)row.get("planYear")).intValue());out.put("planYears",new ArrayList<>(years));}
        if(Set.of("contracts","tuition","deposits").contains(resource(code)))out.put("contractTemplate",contractTemplate.metadata());
        switch(code){
            case "ListAdmissionApplicationDetail"->out.put("enrollmentCourses",AdmissionProfiles.ENROLLMENT_COURSES);
            case "AddClass"->{
                out.put("classrooms",masters.choices("classrooms"));
                out.put("courses",fixedCourses(null));out.put("lecturers",activeStaff(2));
            }
            case "EditClass"->out.put("lecturers",activeStaff(2));
            case "AddTrainingContract","EditTrainingContract"->{
                out.put("enrollmentCourseCatalog",EnrollmentCourses.CATALOG);
                Object currentAdmission=id==null?null:d.record("contracts",id,u).get("consultationId");
                UUID admission=currentAdmission==null?null:uuid(currentAdmission.toString());
                String eligible="(r.intake_source='EXCEL' OR f.forwarded_at IS NOT NULL) AND (f.status IN ('PROFILE_PENDING','PROFILE_CREATED') OR (r.intake_source='EXCEL' AND f.status='CONTACTED') OR (r.id=? AND f.status='CONTRACTED'))";
                out.put("admissions",d.rows("SELECT r.*,f.status,(SELECT CONCAT('file:',w.id,':',w.filename) FROM workflow_files w WHERE w.id=f.scan_file_id) AS profile_evidence FROM consultation_registrations r JOIN consultation_followups f ON f.consultation_id=r.id WHERE "+eligible+" ORDER BY r.created_date DESC,r.id",admission));
                @SuppressWarnings("unchecked") var applications=(List<Map<String,Object>>)out.get("admissions");
                for(var application:applications){application.put("profileDocuments",profiles.documents(uuid(application.get("id").toString())));application.put("contractTemplateDefaults",contractTemplate.admissionDefaults(application,u));}
                var currentContract=id==null?null:d.record("contracts",id,u);
                Object currentCourse=currentContract==null?null:currentContract.get("courseId");
                out.put("courses",d.rows("SELECT scoped.* FROM ("+d.base("courses",u)+") scoped WHERE (fixed_catalog=TRUE AND id IN ("+FixedCourseCatalog.ID_SQL+") AND status IN ('OPEN','ACTIVE')) OR id=? ORDER BY name",currentCourse==null?null:uuid(currentCourse.toString())));
                Object currentClass=currentContract==null?null:currentContract.get("classId");
                UUID classId=currentClass==null?null:uuid(currentClass.toString()),excluded=id==null?null:uuid(id);
                var classes=d.rows("SELECT scoped.* FROM ("+d.base("classes",u)+") scoped WHERE (course_id IN ("+FixedCourseCatalog.ID_SQL+") AND plan_fixed=TRUE AND status IN ('PLANNED','ACTIVE') AND ends_on>=? AND remaining_slots>0) OR id=? ORDER BY plan_year DESC,starts_on,code",LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")),classId);
                for(var cl:classes)cl.put("remainingSlots",Math.max(0,((Number)cl.get("capacity")).longValue()-ClassReservations.occupied(d,uuid(cl.get("id").toString()),excluded)));
                out.put("classes",classes);
            }
            case "TrainingContractDetail"->out.put("admissionsStaff",d.rows("SELECT user_id AS id,full_name AS name FROM m_user u WHERE role_id=7 AND account_enabled=TRUE AND account_locked=FALSE AND EXISTS(SELECT 1 FROM role_function rf WHERE rf.role_id=u.role_id AND rf.function_code='AdmissionContractSigning') ORDER BY full_name"));
            case "ContractSignature"->out.put("accountants",d.rows("SELECT user_id AS id,full_name AS name FROM m_user WHERE role_id=4 AND account_enabled=TRUE AND account_locked=FALSE ORDER BY full_name"));
            case "TimeTable_EditSession","TimeTable_Manager","TimeTable_Conflict"->{out.put("classes",d.rows("SELECT scoped.* FROM ("+d.base("classes",u)+") scoped WHERE status IN ('PLANNED','ACTIVE') ORDER BY code"));out.put("classrooms",masters.choices("classrooms"));}
            case "AddEquipmentRequest"->{out.put("classes",choices("classes",u));out.put("students",d.rows("SELECT DISTINCT s.user_id AS id,s.full_name AS name,e.class_id FROM training_enrollments e JOIN m_user s ON s.user_id=e.student_id JOIN training_classes c ON c.id=e.class_id WHERE e.status='ACTIVE' AND e.academic_completed_at IS NULL AND e.academic_failed_at IS NULL AND "+classScope(u,"c")));}
            case "AssetHandover"->out.put("assets",d.rows("SELECT id,code,name FROM equipment_assets WHERE status='AVAILABLE' ORDER BY code"));
            case "AssetMaintenance"->{out.put("assets",choices("assets",u));out.put("itUsers",d.rows("SELECT user_id AS id,full_name AS name FROM m_user WHERE role_id=5 AND account_enabled=TRUE AND account_locked=FALSE ORDER BY full_name"));}
            case "SendNotification"->out.put("students",choices("students",u));
            case "ListPayment"->{out.put("contracts",choices("contracts",u));out.put("banks",masters.choices("banks"));}
            case "ListAssetIncident","AssetIncidentDetail"->{out.put("assets",choices("assets",u));out.put("loans",choices("loans",u));}
            default->{}
        }
        return out;
    }
    @Transactional
    public Map<String,Object> save(String code,String id,Map<String,Object> p){
        User u=access(code);
        return switch(code){
            case "AdmissionConsultation"->training.consultation(required(id),p,u);
            case "EditStudentProfile"->training.student(required(id),p,u);
            case "AddTrainingContract"->training.contract(null,p,u);case "EditTrainingContract"->training.contract(required(id),p,u);case "ContractSignature"->signing.review(required(id),p,u);
            case "AddClass"->classPlanning.create(p,u);case "EditClass"->classPlanning.update(required(id),p,u);
            case "TimeTable_EditSession"->classPlanning.session(id,p,u);
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
            case "TrainingContractDetail:send-for-signature"->{permissions.require("ContractSignature");yield signing.send(requiredId,p,u);}
            case "AdmissionContractSigning:submit-scan"->signing.submit(requiredId,p,u);
            case "ListAdmissionApplicationDetail:submit-profile"->{permissions.require("AdmissionConsultation");yield profiles.submit(requiredId,p,u);}
            case "ListAdmissionApplicationDetail:update-profile"->{permissions.require("AdmissionConsultation");yield profiles.update(requiredId,p,u);}
            case "ListAdmissionApplicationDetail:update-course"->{permissions.require("AdmissionConsultation");yield profiles.updateCourse(requiredId,p,u);}
            case "ListAdmissionEnrollment:forward-profiles"->{permissions.require("AdmissionConsultation");yield admissionForwarding.forward(p,u);}
            case "ClassDetail:add-students"->training.addClassStudents(requiredId,p,u);
            case "AutoAssignmentDetail:confirm"->training.assignment(requiredId,p,u);
            case "ClassAttendance:save"->training.attendance(requiredId,p,u,false);
            case "ClassGradebook:save"->training.grades(requiredId,p,u,false);
            case "ClassGradebook:publish"->training.grades(requiredId,p,u,true);
            case "EquipmentRequestDetail_IT:prepare","EquipmentRequestDetail_IT:ready","EquipmentRequestDetail_IT:reject"->equipment.requestState(requiredId,p,u,action);
            case "AssetDetail:complete-maintenance"->equipment.completeMaintenance(requiredId,p,u);
            case "ListAssetDetail:confirm","Recovery_Withdrawal:confirm"->{
                var result=equipment.recover(requiredId,p,u);
                for(var enrollment:retakes.failedEnrollmentsForLoan(uuid(requiredId)))
                    retakes.offerIfReadyForEnrollment(uuid(enrollment.get("id").toString()),u);
                yield result;
            }
            case "ListAssetDetail:request-refund","Recovery_Withdrawal:request-refund"->finance.requestRefund(code,requiredId,p,u);
            case "ListAssetIncident:create"->equipment.incident(null,p,u);
            case "AssetIncidentDetail:save"->equipment.incident(requiredId,p,u);
            case "AssetIncidentDetail_Accountant:settle"->finance.incident(requiredId,p,u);
            case "ListPayment:import"->finance.importPayment(p,u);
            case "PaymentDetail:confirm","PaymentDetail:mismatch","PaymentDetail_Mismatch:resolve"->finance.payment(requiredId,p,u,action);
            case "TuitionDetail:activate-zero"->finance.activateZeroTuition(requiredId,p,u);
            case "TrainingClosureDetail:academic"->training.close(requiredId,p,u,action);
            case "TrainingClosureDetail:academic-fail"->{
                var result=training.close(requiredId,p,u,action);
                retakes.offerIfReadyForEnrollment(uuid(requiredId),u);
                yield result;
            }
            case "TrainingClosureDetail_Ready:close","TrainingClosureDetail_Withdrawal:close"->training.close(requiredId,p,u,action);
            case "StudentRetake:register"->retakes.register(requiredId,p,u);
            case "ViewNotification:read","ListNotification:read"->{UUID key=uuid(requiredId);d.record("notifications",requiredId,u);d.jdbc.update("UPDATE user_notifications SET read_at=COALESCE(read_at,CURRENT_TIMESTAMP) WHERE id=? AND recipient_id=?",key,u.id);yield TrainingWorkflows.saved("ViewNotification",key);}
            default->throw new AccessDeniedException("Không có quyền thực hiện thao tác này");
        };
    }
    String required(String id){if(id==null||id.isBlank())throw invalid("id","Chọn bản ghi trước khi thực hiện");return id;}
    private List<Map<String,Object>> activeStaff(int role){return d.rows("SELECT user_id AS id,full_name AS name FROM m_user WHERE role_id=? AND account_enabled=TRUE AND account_locked=FALSE ORDER BY full_name,user_id",role);}
    private List<Map<String,Object>> fixedCourses(Integer year){
        return d.rows("SELECT co.id,co.code,co.name,co.duration_months,co.tuition_fee,co.status,co.class_capacity,co.fixed_catalog,(SELECT COUNT(*) FROM training_classes cl WHERE cl.course_id=co.id"+(year==null?"":" AND cl.plan_year=?")+") AS class_count FROM training_courses co WHERE co.fixed_catalog=TRUE AND co.id IN ("+FixedCourseCatalog.ID_SQL+") ORDER BY co.id",year==null?new Object[0]:new Object[]{year});
    }
    private Map<String,Object> courseSchedules(User actor,String q,String status,int page,Integer year,String course){
        role(actor,"ADMIN");if(year!=null&&(year<1900||year>9999))throw invalid("year","Năm kế hoạch không hợp lệ");
        UUID courseId=course.isBlank()?null:uuid(course);if(courseId!=null&&(!FixedCourseCatalog.IDS.contains(courseId)||d.count("SELECT COUNT(*) FROM training_courses WHERE id=? AND fixed_catalog=TRUE",courseId)==0))throw invalid("courseId","Chọn một trong bốn khóa đào tạo cố định");
        if(!status.isBlank()&&!Set.of("PLANNED","ACTIVE","COMPLETED","CANCELLED").contains(status))throw invalid("status","Trạng thái lớp không hợp lệ");
        List<Object> args=new ArrayList<>();String sql="SELECT scoped.* FROM ("+d.base("classes",actor)+") scoped WHERE course_id IN (SELECT id FROM training_courses WHERE fixed_catalog=TRUE AND id IN ("+FixedCourseCatalog.ID_SQL+"))";
        if(year!=null){sql+=" AND plan_year=?";args.add(year);}if(courseId!=null){sql+=" AND course_id=?";args.add(courseId);}
        if(!q.isBlank()){sql+=" AND LOWER(name||' '||code||' '||COALESCE(lecturer_name,'')) LIKE ? ESCAPE '!'";args.add("%"+q.toLowerCase(Locale.ROOT).replace("!","!!").replace("%","!%").replace("_","!_")+"%");}
        if(!status.isBlank()){sql+=" AND status=?";args.add(status);}
        long total=d.count("SELECT COUNT(*) FROM ("+sql+") plans",args.toArray());args.add(page*25);var plans=d.rows(sql+" ORDER BY plan_year DESC,starts_on,code LIMIT 25 OFFSET ?",args.toArray());
        var courses=fixedCourses(year);var options=new LinkedHashMap<String,Object>();options.put("courses",courses);options.put("lecturers",activeStaff(2));options.put("classrooms",masters.choices("classrooms"));
        SortedSet<Integer> years=new TreeSet<>(Comparator.reverseOrder());years.add(LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")).getYear());for(var row:d.rows("SELECT DISTINCT plan_year FROM training_classes WHERE plan_year IS NOT NULL AND course_id IN (SELECT id FROM training_courses WHERE fixed_catalog=TRUE AND id IN ("+FixedCourseCatalog.ID_SQL+"))"))years.add(((Number)row.get("planYear")).intValue());options.put("planYears",new ArrayList<>(years));
        Map<String,Object> result=new LinkedHashMap<>();result.put("resource","classes");result.put("canWrite",true);result.put("courses",courses);result.put("fixedCourses",courses);result.put("items",plans);result.put("plans",plans);result.put("options",options);result.put("total",total);result.put("page",page);result.put("totalPages",(total+24)/25);result.put("year",year);result.put("courseId",courseId==null?null:courseId.toString());return result;
    }
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

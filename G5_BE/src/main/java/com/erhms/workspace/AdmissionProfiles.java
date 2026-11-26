package com.erhms.workspace;

import com.erhms.user.entity.User;
import java.util.*;
import org.springframework.stereotype.Service;
import static com.erhms.workspace.WorkflowData.*;

@Service
public class AdmissionProfiles {
    public static final List<String> ENROLLMENT_COURSES=EnrollmentCourses.NAMES;
    private static final Set<String> TYPES=Set.of("IDENTITY_CARD","CURRICULUM_VITAE","BIRTH_CERTIFICATE","ID_PHOTO","APPLICATION_FORM");
    private static final Set<String> INITIAL_SUBMISSION_STATES=Set.of("NEW","CONTACTED","NEEDS_COUNSELING","DOCUMENT_APPOINTMENT");
    private static final int MAX_FILES=50;
    // Completeness is derived from the current categorized files, independently of contract/account progress.
    public static final String DOCUMENT_STATUS_SQL="CASE WHEN f.profile_submitted_at IS NOT NULL OR f.status IN ('PROFILE_PENDING','CONTRACTED','PROFILE_CREATED') THEN CASE WHEN (SELECT COUNT(DISTINCT doc.document_type) FROM admission_profile_documents doc WHERE doc.consultation_id=r.id AND doc.document_type IN ('IDENTITY_CARD','CURRICULUM_VITAE','BIRTH_CERTIFICATE','ID_PHOTO','APPLICATION_FORM'))=5 THEN 'DOCUMENTS_COMPLETE' ELSE 'DOCUMENTS_INCOMPLETE' END END";
    private final WorkflowData data;

    public AdmissionProfiles(WorkflowData data) { this.data = data; }

    public Map<String,Object> updateCourse(String id,Map<String,Object> input,User actor) {
        role(actor,"HR");
        UUID admission=uuid(id);
        data.record("admissions",id,actor);
        // Handoff takes this same lock, so a concurrent edit cannot change the handed-off course.
        data.one("SELECT id FROM consultation_registrations WHERE id=? FOR UPDATE",admission);
        var record=data.record("admissions",id,actor);
        if(record.get("forwardedAt")!=null)throw conflict("Hồ sơ đã chuyển học vụ; không thể thay đổi khóa học đăng ký.");
        if(number(input,"version",0,Long.MAX_VALUE)!=((Number)record.get("version")).longValue())
            throw conflict("Hồ sơ vừa được cập nhật. Hãy tải lại trước khi đổi khóa học.");
        String course=text(input,"enrollmentCourse",255,true);
        if(!ENROLLMENT_COURSES.contains(course))throw invalid("enrollmentCourse","Chọn một trong 4 khóa học được hỗ trợ.");
        if(course.equals(record.get("enrollmentCourse")))return TrainingWorkflows.saved("ListAdmissionApplicationDetail",id);
        data.jdbc.update("UPDATE consultation_registrations SET enrollment_course=? WHERE id=?",course,admission);
        if(data.count("SELECT COUNT(*) FROM consultation_followups WHERE consultation_id=?",admission)==0)
            data.jdbc.update("INSERT INTO consultation_followups(id,consultation_id,assigned_to,status,version) VALUES (?,?,?,'NEW',1)",UUID.randomUUID(),admission,actor.id);
        else data.jdbc.update("UPDATE consultation_followups SET assigned_to=COALESCE(assigned_to,?),version=version+1,updated_at=CURRENT_TIMESTAMP WHERE consultation_id=?",actor.id,admission);
        String previous=Objects.toString(record.get("enrollmentCourse"),"Chưa chọn");
        data.jdbc.update("INSERT INTO consultation_notes(id,consultation_id,actor_id,channel,outcome,content) VALUES (?,?,?,'IN_PERSON','COURSE_UPDATED',?)",UUID.randomUUID(),admission,actor.id,"Đổi khóa học đăng ký từ “"+previous+"” sang “"+course+"”.");
        return Map.of("screen","ListAdmissionApplicationDetail","id",id,"notice","Đã lưu khóa học đăng ký.");
    }

    public List<Map<String,Object>> documents(UUID admission) {
        return data.rows("SELECT d.id,d.document_type,d.display_order,d.created_at,w.id AS file_id,w.filename,w.content_type,w.file_size,CONCAT('file:',w.id,':',w.filename) AS reference FROM admission_profile_documents d JOIN workflow_files w ON w.id=d.file_id WHERE d.consultation_id=? ORDER BY d.display_order,d.id",admission);
    }

    public static boolean submitted(Map<String,Object> record) {
        return record.get("profileSubmittedAt")!=null||Set.of("PROFILE_PENDING","PROFILE_CREATED","CONTRACTED").contains(record.get("status"));
    }

    public static boolean canSubmit(Map<String,Object> record) {
        return INITIAL_SUBMISSION_STATES.contains(Objects.toString(record.get("status"),""))
                &&record.get("forwardedAt")==null&&!submitted(record);
    }

    public Map<String,Object> submit(String id, Map<String,Object> input, User actor) {
        return save(id,input,actor,false);
    }

    public Map<String,Object> update(String id, Map<String,Object> input, User actor) {
        return save(id,input,actor,true);
    }

    private Map<String,Object> save(String id, Map<String,Object> input, User actor,boolean editing) {
        role(actor, "HR");
        UUID admission = uuid(id);
        data.record("admissions", id, actor);
        data.one("SELECT id FROM consultation_registrations WHERE id=? FOR UPDATE", admission);
        var record = data.record("admissions", id, actor);
        if (number(input, "version", 0, Long.MAX_VALUE) != ((Number)record.get("version")).longValue())
            throw conflict("Hồ sơ vừa được cập nhật. Hãy tải lại trước khi gửi.");
        if(editing&&!submitted(record))throw conflict("Chỉ bổ sung hoặc chỉnh sửa bộ hồ sơ đã gửi.");
        if(!editing&&!canSubmit(record))throw conflict("Hồ sơ hiện tại không thể tiếp nhận bản scan lần đầu.");
        var existing=documents(admission);
        Map<UUID,String> existingTypes=new HashMap<>();
        for(var document:existing)existingTypes.put(UUID.fromString(document.get("fileId").toString()),document.get("documentType").toString());
        if(!(input.get("documents") instanceof List<?>))throw invalid("documents","Cung cấp danh sách giấy tờ cần lưu.");
        var groups=items(input,"documents");
        if((!editing&&groups.isEmpty())||groups.size()>TYPES.size()+1)throw invalid("documents","Tải ít nhất một file PDF cho hồ sơ; mỗi loại giấy tờ chỉ xuất hiện một lần.");
        Set<String> seenTypes=new HashSet<>();Set<UUID> seenFiles=new HashSet<>();
        List<Map.Entry<String,UUID>> attachments=new ArrayList<>();
        for(int index=0;index<groups.size();index++) {
            var group=groups.get(index);String field="documents."+index+".files";
            String type=text(group,"type",40,true);
            if((!TYPES.contains(type)&&!(editing&&type.equals("LEGACY_SCAN")))||!seenTypes.add(type))throw invalid("documents."+index+".type","Loại giấy tờ không hợp lệ hoặc bị chọn lặp.");
            if(!(group.get("files") instanceof List<?> files)||files.isEmpty()||files.size()>MAX_FILES)
                throw invalid(field,"Mỗi mục đã chọn cần ít nhất một file PDF; toàn bộ hồ sơ tối đa 50 tệp.");
            for(Object raw:files) {
                UUID file;
                try { file=UUID.fromString(raw instanceof String value?value:""); }
                catch(IllegalArgumentException failure) { throw invalid(field,"Chọn tệp PDF đã tải lên cho giấy tờ này."); }
                if(!seenFiles.add(file)||attachments.size()>=MAX_FILES)throw invalid(field,"Tệp bị chọn lặp hoặc hồ sơ vượt quá 50 tệp.");
                boolean retained=editing&&type.equals(existingTypes.get(file));
                if(type.equals("LEGACY_SCAN")&&!retained)throw invalid(field,"Chỉ giữ lại bản scan cũ đã có trong bộ hồ sơ này.");
                var stored=data.rows("SELECT content_type,uploaded_by FROM workflow_files WHERE id=?",file);
                if(stored.size()!=1||(!retained&&((Number)stored.get(0).get("uploadedBy")).longValue()!=actor.id))throw invalid(field,"Tệp không tồn tại hoặc không phải tệp bạn đã tải lên.");
                if(!"application/pdf".equals(stored.get(0).get("contentType"))&&!(retained&&type.equals("LEGACY_SCAN")))throw invalid(field,"Giấy tờ hồ sơ chỉ nhận file PDF.");
                attachments.add(Map.entry(type,file));
            }
        }
        String note = text(input, "note", 1800, false);
        if(!editing&&!existing.isEmpty())throw conflict("Hồ sơ đã có giấy tờ gửi quản lý. Hãy tải lại để kiểm tra.");
        if(editing)data.jdbc.update("DELETE FROM admission_profile_documents WHERE consultation_id=?",admission);
        for(int index=0;index<attachments.size();index++) {
            var attachment=attachments.get(index);
            data.jdbc.update("INSERT INTO admission_profile_documents(id,consultation_id,document_type,file_id,submitted_by,display_order) VALUES (?,?,?,?,?,?)",UUID.randomUUID(),admission,attachment.getKey(),attachment.getValue(),actor.id,index);
        }
        UUID scan=attachments.isEmpty()?null:attachments.get(0).getValue();
        int updated=data.jdbc.update("""
                UPDATE consultation_followups SET assigned_to=COALESCE(assigned_to,?),scan_file_id=?,
                    profile_submitted_at=COALESCE(profile_submitted_at,CURRENT_TIMESTAMP),status=?,version=version+1,updated_at=CURRENT_TIMESTAMP
                WHERE consultation_id=?
                """,editing?record.get("assignedTo"):actor.id,scan,editing?record.get("status"):"PROFILE_PENDING",admission);
        if(updated==0)data.jdbc.update("""
                INSERT INTO consultation_followups(id,consultation_id,assigned_to,scan_file_id,profile_submitted_at,status,version)
                VALUES (?,?,?,?,CURRENT_TIMESTAMP,'PROFILE_PENDING',1)
                """,UUID.randomUUID(),admission,actor.id,scan);
        boolean complete=seenTypes.containsAll(TYPES);
        String documentStatus=complete?"DOCUMENTS_COMPLETE":"DOCUMENTS_INCOMPLETE",label=complete?"Đã hoàn thành hồ sơ":"Hồ sơ còn thiếu";
        long added=seenFiles.stream().filter(file->!existingTypes.containsKey(file)).count(),removed=existingTypes.keySet().stream().filter(file->!seenFiles.contains(file)).count();
        String description=(editing?"Đã cập nhật ":"Đã gửi ")+attachments.size()+" tệp PDF thuộc "+groups.size()+" loại giấy tờ. "+label+"."+(editing?" Thêm "+added+" tệp, bỏ "+removed+" tệp.":"");
        data.jdbc.update("INSERT INTO consultation_notes(id,consultation_id,actor_id,channel,outcome,content) VALUES (?,?,?,'IN_PERSON',?,?)",UUID.randomUUID(),admission,actor.id,documentStatus,description+(note.isEmpty()?"":" "+note));
        return Map.of("screen","ListAdmissionApplicationDetail","id",id,"notice",description);
    }
}

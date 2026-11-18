package com.erhms.workspace;

import com.erhms.admin.service.ScreenPermissionsService;
import com.erhms.user.entity.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;
import static com.erhms.workspace.WorkflowData.*;

@Service
public class ContractSigningWorkflow {
    final WorkflowData d;
    final ContractTemplateData template;
    final ContractPdfRenderer renderer;
    final ScreenPermissionsService permissions;

    public ContractSigningWorkflow(WorkflowData d,ContractTemplateData template,ContractPdfRenderer renderer,ScreenPermissionsService permissions){
        this.d=d;this.template=template;this.renderer=renderer;this.permissions=permissions;
    }

    private String admissionsScope(User u){
        role(u,"HR");
        return "c.signature_admissions_id="+u.id+" AND c.signature_sent_at IS NOT NULL";
    }
    private String select(){return "SELECT c.*,COALESCE(c.applicant_name,s.full_name) AS student_name,COALESCE(c.registered_course_name,co.name) AS course_name,m.full_name AS manager_name,a.full_name AS signature_admissions_name FROM training_contracts c LEFT JOIN m_user s ON s.user_id=c.student_id LEFT JOIN training_courses co ON co.id=c.course_id LEFT JOIN m_user m ON m.user_id=c.manager_id LEFT JOIN m_user a ON a.user_id=c.signature_admissions_id";}

    public Map<String,Object> admissionRecord(String id,User u){
        return d.one(select()+" WHERE c.id=? AND "+admissionsScope(u),uuid(id));
    }

    public List<Map<String,Object>> history(UUID id){
        return d.rows("SELECT h.id,h.action,h.note,h.file_id,h.created_at,u.full_name AS actor_name,w.filename FROM contract_signing_history h JOIN m_user u ON u.user_id=h.actor_id LEFT JOIN workflow_files w ON w.id=h.file_id WHERE h.contract_id=? ORDER BY h.created_at DESC,h.id",id);
    }

    @Transactional(readOnly=true)
    public Map<String,Object> view(String id,String q,String status,int page,User u){
        String scope=admissionsScope(u);
        Map<String,Object> out=new LinkedHashMap<>();out.put("resource","contract-signing");out.put("canWrite",true);out.put("options",Map.of());
        if(id!=null){
            var record=admissionRecord(id,u);out.put("record",record);out.put("canExportContract",true);
            out.put("related",Map.of("signingHistory",history(uuid(id))));return out;
        }
        if(page<0||page>100000||q.length()>200||status.length()>40)throw invalid("filter","Bộ lọc không hợp lệ");
        String search="%"+q.strip().toLowerCase(Locale.ROOT).replace("!","!!").replace("%","!%").replace("_","!_")+"%";
        String condition=" WHERE "+scope+" AND LOWER(COALESCE(c.applicant_name,'')||' '||COALESCE(c.student_email,'')||' '||c.code) LIKE ? ESCAPE '!' AND (?='' OR c.status=?)";
        out.put("items",d.rows(select()+condition+" ORDER BY CASE c.status WHEN 'SENT_FOR_SIGNATURE' THEN 0 WHEN 'SIGNATURE_REVIEW' THEN 1 ELSE 2 END,c.signature_sent_at DESC,c.id LIMIT 25 OFFSET ?",search,status,status,page*25));
        long total=d.count("SELECT COUNT(*) FROM training_contracts c"+condition,search,status,status);
        out.put("total",total);out.put("page",page);out.put("size",25);out.put("totalPages",(total+24)/25);return out;
    }

    @Transactional
    public Map<String,Object> send(String id,Map<String,Object> p,User u){
        role(u,"MANAGER");var c=d.record("contracts",id,u);UUID key=uuid(id);
        if(!"DRAFT".equals(c.get("status")))throw conflict("Chỉ gửi hợp đồng bản nháp cho tuyển sinh ký.");
        var followups=c.get("consultationId")==null?List.<Map<String,Object>>of():d.rows("SELECT assigned_to FROM consultation_followups WHERE consultation_id=?",uuid(c.get("consultationId").toString()));
        Object assigned=followups.isEmpty()?null:followups.get(0).get("assignedTo");
        long admissions;
        if(p.get("admissionsId")!=null&&!p.get("admissionsId").toString().isBlank())admissions=number(p,"admissionsId",1,Long.MAX_VALUE);
        else if(assigned instanceof Number actor)admissions=actor.longValue();
        else throw invalid("admissionsId","Chọn nhân viên tuyển sinh nhận hợp đồng để in và lấy chữ ký.");
        lockPeople(c,u,admissions);d.lock("training_contracts",key,p);c=d.record("contracts",id,u);
        if(!"DRAFT".equals(c.get("status")))throw conflict("Chỉ gửi hợp đồng bản nháp cho tuyển sinh ký.");
        var staff=d.rows("SELECT user_id FROM m_user u WHERE u.user_id=? AND u.role_id=7 AND u.account_enabled=TRUE AND u.account_locked=FALSE AND EXISTS(SELECT 1 FROM role_function rf WHERE rf.role_id=u.role_id AND rf.function_code='AdmissionContractSigning')",admissions);
        if(staff.isEmpty())throw invalid("admissionsId","Nhân viên tuyển sinh cần đang hoạt động và có quyền In & ký hợp đồng.");
        var values=template.values(c,u);template.requireComplete(values);values.put("additionalTerms",Objects.toString(c.get("terms"),""));
        byte[] pdf=renderer.pdf(values);UUID file=UUID.randomUUID();
        String filename="Hop-dong-"+c.get("code")+".pdf";
        d.jdbc.update("INSERT INTO workflow_files(id,uploaded_by,filename,content_type,file_size,file_data,upload_screen) VALUES (?,?,?,'application/pdf',?,?,'TrainingContractDetail')",file,u.id,filename,pdf.length,pdf);
        d.jdbc.update("UPDATE training_contracts SET status='SENT_FOR_SIGNATURE',signature_admissions_id=?,signature_sent_at=CURRENT_TIMESTAMP,print_file_id=?,signature_review_note=NULL,version=version+1 WHERE id=?",admissions,file,key);
        audit(key,u,"SENT",file,"Gửi tuyển sinh in hợp đồng và lấy chữ ký học viên.");
        notify(admissions,u,"Hợp đồng cần in và ký","Hợp đồng "+c.get("code")+" đã được QLHV gửi. In bản PDF, lấy chữ ký và tải bản scan lên.","AdmissionContractSigning",id);
        return result("TrainingContractDetail",id,"Đã gửi hợp đồng sang tuyển sinh để in và lấy chữ ký.");
    }

    @Transactional
    public Map<String,Object> submit(String id,Map<String,Object> p,User u){
        role(u,"HR");var c=admissionRecord(id,u);UUID key=uuid(id);
        if(!"SENT_FOR_SIGNATURE".equals(c.get("status")))throw conflict("Chỉ tải bản ký khi hợp đồng đang chờ tuyển sinh lấy chữ ký.");
        lockPeople(c,u);d.lock("training_contracts",key,p);c=admissionRecord(id,u);
        if(!"SENT_FOR_SIGNATURE".equals(c.get("status")))throw conflict("Chỉ tải bản ký khi hợp đồng đang chờ tuyển sinh lấy chữ ký.");
        LocalDate signed=date(p,"signedOn",true);
        if(signed.isAfter(LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh"))))throw invalid("signedOn","Ngày ký không được ở tương lai.");
        UUID scan=uuid(p,"signedScanId");
        var files=d.rows("SELECT id,uploaded_by,content_type,upload_screen FROM workflow_files WHERE id=? FOR UPDATE",scan);
        if(files.isEmpty())throw invalid("signedScanId","Tải lên bản scan hợp đồng đã ký.");
        var file=files.get(0);
        if(((Number)file.get("uploadedBy")).longValue()!=u.id||!"application/pdf".equals(file.get("contentType"))||!"AdmissionContractSigning".equals(file.get("uploadScreen")))throw invalid("signedScanId","Chọn bản scan PDF do bạn tải lên tại màn In & ký hợp đồng.");
        if(d.count("SELECT COUNT(*) FROM contract_signing_history WHERE file_id=? AND action='SUBMITTED'",scan)>0)throw invalid("signedScanId","Bản scan này đã được gửi. Hãy tải một bản scan mới để đối chiếu.");
        String filename=d.one("SELECT filename FROM workflow_files WHERE id=?",scan).get("filename").toString();
        d.jdbc.update("UPDATE training_contracts SET status='SIGNATURE_REVIEW',signed_on=?,signature_method='PAPER',signed_scan_file_id=?,signature_evidence=?,signature_submitted_at=CURRENT_TIMESTAMP,signature_submitted_by=?,signature_review_note=NULL,version=version+1 WHERE id=?",signed,scan,"file:"+scan+":"+filename,u.id,key);
        audit(key,u,"SUBMITTED",scan,"Tuyển sinh gửi bản scan đã ký, chờ QLHV xác nhận.");
        notify(((Number)c.get("managerId")).longValue(),u,"Bản ký hợp đồng chờ xác nhận","Tuyển sinh đã tải bản scan hợp đồng "+c.get("code")+". Kiểm tra và xác nhận trước khi cấp tài khoản.","ContractSignature",id);
        return result("AdmissionContractSigning",id,"Đã gửi bản scan cho QLHV xác nhận.");
    }

    @Transactional
    public Map<String,Object> review(String id,Map<String,Object> p,User u){
        role(u,"MANAGER");var c=d.record("contracts",id,u);UUID key=uuid(id);
        if(!"SIGNATURE_REVIEW".equals(c.get("status"))||c.get("signedScanFileId")==null)throw conflict("Cần tuyển sinh gửi bản scan đã ký trước khi QLHV xác nhận.");
        String decision=choice(p,"decision","APPROVE","REJECT"),note=text(p,"reviewNote",1000,decision.equals("REJECT"));
        Long accountant=decision.equals("APPROVE")?number(p,"accountantId",1,Long.MAX_VALUE):null;
        lockPeople(c,u,accountant);d.lock("training_contracts",key,p);c=d.record("contracts",id,u);
        if(!"SIGNATURE_REVIEW".equals(c.get("status"))||c.get("signedScanFileId")==null)throw conflict("Cần tuyển sinh gửi bản scan đã ký trước khi QLHV xác nhận.");
        UUID scan=uuid(c.get("signedScanFileId").toString());
        if(decision.equals("REJECT")){
            d.jdbc.update("UPDATE training_contracts SET status='SENT_FOR_SIGNATURE',signature_review_note=?,version=version+1 WHERE id=?",note,key);
            audit(key,u,"REJECTED",scan,note);
            notify(((Number)c.get("signatureAdmissionsId")).longValue(),u,"Bản ký hợp đồng cần bổ sung","Hợp đồng "+c.get("code")+": "+note,"AdmissionContractSigning",id);
            return result("TrainingContractDetail",id,"Đã yêu cầu tuyển sinh bổ sung bản scan đã ký.");
        }
        d.requireRoleId(accountant,4);
        String handover=text(p,"handoverNote",1000,false);
        // Finance receives the contract only once the account has also been provisioned.
        d.jdbc.update("UPDATE training_contracts SET status='SIGNED',signature_approved_at=CURRENT_TIMESTAMP,signature_approved_by=?,signature_review_note=?,accountant_id=?,handover_note=?,handed_over_at=CASE WHEN student_id IS NOT NULL AND (consultation_id IS NULL OR EXISTS(SELECT 1 FROM consultation_followups f WHERE f.consultation_id=training_contracts.consultation_id AND f.profile_student_id=training_contracts.student_id AND f.profile_created_at IS NOT NULL)) THEN CURRENT_TIMESTAMP ELSE NULL END,version=version+1 WHERE id=?",u.id,note,accountant,handover,key);
        audit(key,u,"APPROVED",scan,note);
        notify(((Number)c.get("signatureAdmissionsId")).longValue(),u,"Bản ký hợp đồng đã được xác nhận","QLHV đã xác nhận bản ký hợp đồng "+c.get("code")+".","AdmissionContractSigning",id);
        return result("TrainingContractDetail",id,"Đã xác nhận hợp đồng đã ký. Có thể cấp tài khoản học viên.");
    }

    private void lockPeople(Map<String,Object> contract,User actor,Long...extra){
        // Provisioning locks the Manager before the contract. Lock all notification/FK
        // participants first, in stable order, so HR submission and review agree too.
        var ids=new TreeSet<Long>();ids.add(actor.id);
        for(String key:List.of("managerId","signatureAdmissionsId"))if(contract.get(key) instanceof Number id)ids.add(id.longValue());
        for(Long id:extra)if(id!=null)ids.add(id);
        String placeholders=String.join(",",Collections.nCopies(ids.size(),"?"));
        d.rows("SELECT user_id FROM m_user WHERE user_id IN ("+placeholders+") ORDER BY user_id FOR UPDATE",ids.toArray());
        d.requireRoleId(actor.id,actor.getRoleCode().equals("MANAGER")?6:7);
    }
    private void audit(UUID contract,User actor,String action,UUID file,String note){d.jdbc.update("INSERT INTO contract_signing_history(id,contract_id,actor_id,action,file_id,note) VALUES (?,?,?,?,?,?)",UUID.randomUUID(),contract,actor.id,action,file,note);}
    private void notify(long recipient,User actor,String title,String content,String screen,String id){d.jdbc.update("INSERT INTO user_notifications(id,recipient_id,sender_id,title,content,target_screen,target_id) VALUES (?,?,?,?,?,?,?)",UUID.randomUUID(),recipient,actor.id,title,content,screen,id);}
    private Map<String,Object> result(String screen,String id,String notice){return Map.of("screen",screen,"id",id,"notice",notice);}
}

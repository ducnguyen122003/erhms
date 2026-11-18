package com.erhms.workspace;

import com.erhms.admin.service.ScreenPermissionsService;
import com.erhms.user.entity.User;
import com.erhms.user.service.CurrentUser;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.security.access.AccessDeniedException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static com.erhms.workspace.WorkflowData.*;

@RestController
@RequestMapping("/api/workspace/files")
public class WorkflowFileController {
    final WorkflowData d;final CurrentUser current;final ScreenPermissionsService permissions;
    public WorkflowFileController(WorkflowData d,CurrentUser current,ScreenPermissionsService permissions){this.d=d;this.current=current;this.permissions=permissions;}
    @PostMapping(consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String,Object> upload(@RequestParam MultipartFile file,@RequestParam String screen) throws IOException {
        User u=current.get();role(u,"HR","MANAGER","IT_SUPPORT","ACCOUNTANT");
        if(!Set.of("AdmissionConsultation","AdmissionContractSigning","RecordWithdrawal","AssetHandover","ListAssetDetail","Recovery_Withdrawal","AssetIncidentDetail","AssetIncidentDetail_Accountant","DepositRefund","ListPayment","ListAssetIncident").contains(screen))throw new AccessDeniedException("Màn không hỗ trợ tải minh chứng");permissions.require(screen);if(Set.of("AdmissionConsultation","AdmissionContractSigning").contains(screen))role(u,"HR");
        boolean signedScan=screen.equals("AdmissionContractSigning");long limit=signedScan?10485760:2097152;
        if(file.isEmpty()||file.getSize()>limit)throw invalid(signedScan?"signedScanId":"evidence","Tệp cần có dữ liệu và không vượt quá "+(signedScan?"10":"2")+" MB");
        byte[] bytes=file.getBytes();String type;
        if(bytes.length>=5&&new String(bytes,0,5,StandardCharsets.US_ASCII).equals("%PDF-"))type="application/pdf";
        else if(bytes.length>=8&&bytes[0]==(byte)137&&bytes[1]==80&&bytes[2]==78&&bytes[3]==71)type="image/png";
        else if(bytes.length>=3&&bytes[0]==(byte)255&&bytes[1]==(byte)216&&bytes[2]==(byte)255)type="image/jpeg";
        else throw invalid("evidence","Chỉ nhận PDF, PNG hoặc JPEG");
        if(screen.equals("AdmissionConsultation")&&!type.equals("application/pdf"))throw invalid("documents","Giấy tờ tuyển sinh chỉ nhận file PDF.");
        if(signedScan&&!type.equals("application/pdf"))throw invalid("signedScanId","Bản scan hợp đồng đã ký chỉ nhận file PDF.");
        String filename=Optional.ofNullable(file.getOriginalFilename()).orElse("minh-chung").replaceAll("[\\\\/\\r\\n\\p{Cntrl}]","_");if(filename.length()>160)filename=filename.substring(filename.length()-160);
        UUID id=UUID.randomUUID();d.jdbc.update("INSERT INTO workflow_files(id,uploaded_by,filename,content_type,file_size,file_data,upload_screen) VALUES (?,?,?,?,?,?,?)",id,u.id,filename,type,bytes.length,bytes,screen);
        return Map.of("id",id.toString(),"reference","file:"+id+":"+filename,"filename",filename);
    }
    @GetMapping("/{id}")
    public ResponseEntity<byte[]> download(@PathVariable UUID id){
        User u=current.get();var file=d.one("SELECT id,uploaded_by,filename,content_type FROM workflow_files WHERE id=?",id);
        if(((Number)file.get("uploadedBy")).longValue()!=u.id&&!linked(id,u))throw missing();
        byte[] bytes=d.jdbc.queryForObject("SELECT file_data FROM workflow_files WHERE id=?",byte[].class,id);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(file.get("contentType").toString()))
                .header("X-Content-Type-Options","nosniff").header(HttpHeaders.CACHE_CONTROL,"private, no-store")
                .header(HttpHeaders.CONTENT_DISPOSITION,ContentDisposition.attachment().filename(file.get("filename").toString(),StandardCharsets.UTF_8).build().toString()).body(bytes);
    }
    boolean linked(UUID id,User user){
        var signingGrants=permissions.codes(user);
        if(user.getRoleCode().equals("HR")&&signingGrants.contains("AdmissionContractSigning")
                &&d.count("SELECT COUNT(*) FROM training_contracts c WHERE c.signature_admissions_id=? AND c.signature_sent_at IS NOT NULL AND (c.print_file_id=? OR c.signed_scan_file_id=? OR EXISTS(SELECT 1 FROM contract_signing_history h WHERE h.contract_id=c.id AND h.file_id=?))",user.id,id,id,id)>0)return true;
        if(user.getRoleCode().equals("MANAGER")&&(signingGrants.contains("TrainingContractDetail")||signingGrants.contains("ContractSignature"))
                &&d.count("SELECT COUNT(*) FROM training_contracts c WHERE c.manager_id=? AND (c.print_file_id=? OR c.signed_scan_file_id=? OR EXISTS(SELECT 1 FROM contract_signing_history h WHERE h.contract_id=c.id AND h.file_id=?))",user.id,id,id,id)>0)return true;
        if(user.getRoleCode().equals("MANAGER")&&(permissions.codes(user).contains("ListStudentAccount")||permissions.codes(user).contains("AddTrainingContract"))
                &&d.count("SELECT COUNT(*) FROM admission_profile_documents d JOIN consultation_followups f ON f.consultation_id=d.consultation_id WHERE d.file_id=? AND f.forwarded_at IS NOT NULL AND f.status IN ('PROFILE_PENDING','PROFILE_CREATED')",id)>0)return true;
        if(user.getRoleCode().equals("MANAGER")&&permissions.codes(user).contains("TrainingContractDetail")
                &&d.count("SELECT COUNT(*) FROM admission_profile_documents d JOIN training_contracts c ON c.consultation_id=d.consultation_id WHERE d.file_id=? AND "+contractScope(user,"c"),id)>0)return true;
        if(user.getRoleCode().equals("HR")&&permissions.codes(user).contains("ListAdmissionApplicationDetail")
                &&d.count("SELECT COUNT(*) FROM admission_profile_documents d JOIN consultation_followups f ON f.consultation_id=d.consultation_id JOIN consultation_registrations r ON r.id=f.consultation_id WHERE d.file_id=? AND f.assigned_to=? AND r.intake_source='ADMISSION'",id,user.id)>0)return true;
        var grants=permissions.codes(user);String needle="%file:"+id+":%";
        var resources=Map.of("contracts",List.of("TrainingContractDetail","TuitionDetail_Student","TuitionDetail"),"loans",List.of("StudentAssetReceipt","AssetHandover_Receipt","ListAssetDetail"),"payments",List.of("PaymentDetail"),"incidents",List.of("AssetIncidentDetail","AssetIncidentDetail_Accountant"),"closures",List.of("TrainingClosureDetail"));
        for(var entry:resources.entrySet()){
            if(entry.getValue().stream().noneMatch(grants::contains))continue;
            String condition=switch(entry.getKey()){case "contracts"->"signature_evidence LIKE ?";case "loans"->"receipt_evidence LIKE ? OR return_evidence LIKE ?";case "incidents"->"evidence LIKE ? OR approval_reference LIKE ?";case "closures"->"withdrawal_evidence LIKE ?";default->"evidence LIKE ?";};
            Object[] args=condition.contains(" OR ")?new Object[]{needle,needle}:new Object[]{needle};
            if(d.count("SELECT COUNT(*) FROM ("+d.base(entry.getKey(),user)+") scoped WHERE "+condition,args)>0)return true;
        }return false;
    }
}

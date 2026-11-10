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
        if(!Set.of("ContractSignature","RecordWithdrawal","AssetHandover","ListAssetDetail","Recovery_Withdrawal","AssetIncidentDetail","AssetIncidentDetail_Accountant","DepositRefund","ListPayment","ListAssetIncident").contains(screen))throw new AccessDeniedException("Màn không hỗ trợ tải minh chứng");permissions.require(screen);if(screen.equals("ContractSignature"))role(u,"MANAGER");
        if(file.isEmpty()||file.getSize()>2097152)throw invalid("evidence","Tệp cần có dữ liệu và không vượt quá 2 MB");
        byte[] bytes=file.getBytes();String type;
        if(bytes.length>=5&&new String(bytes,0,5,StandardCharsets.US_ASCII).equals("%PDF-"))type="application/pdf";
        else if(bytes.length>=8&&bytes[0]==(byte)137&&bytes[1]==80&&bytes[2]==78&&bytes[3]==71)type="image/png";
        else if(bytes.length>=3&&bytes[0]==(byte)255&&bytes[1]==(byte)216&&bytes[2]==(byte)255)type="image/jpeg";
        else throw invalid("evidence","Chỉ nhận PDF, PNG hoặc JPEG");
        String filename=Optional.ofNullable(file.getOriginalFilename()).orElse("minh-chung").replaceAll("[\\\\/\\r\\n\\p{Cntrl}]","_");if(filename.length()>160)filename=filename.substring(filename.length()-160);
        UUID id=UUID.randomUUID();d.jdbc.update("INSERT INTO workflow_files(id,uploaded_by,filename,content_type,file_size,file_data) VALUES (?,?,?,?,?,?)",id,u.id,filename,type,bytes.length,bytes);
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

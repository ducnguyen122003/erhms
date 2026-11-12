package com.erhms.workspace;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/workspace/student-accounts")
public class StudentAccountsController {
    private final StudentAccountsService service;
    private final StudentAccountExcelService excel;
    public StudentAccountsController(StudentAccountsService service,StudentAccountExcelService excel) {this.service=service;this.excel=excel;}

    @GetMapping("/import-template")
    public ResponseEntity<byte[]> template() {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header(HttpHeaders.CONTENT_DISPOSITION,ContentDisposition.attachment().filename("mau-nhap-hoc-vien.xlsx").build().toString())
                .header(HttpHeaders.CACHE_CONTROL,"private, no-store")
                .header("X-Content-Type-Options","nosniff")
                .body(excel.template());
    }

    @PostMapping(value="/import",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String,Object> importFile(@RequestParam(value="file",required=false) MultipartFile file,HttpServletRequest request) {
        request.setAttribute("workflow.action","STUDENT_ACCOUNTS_IMPORT");
        request.setAttribute("workflow.label","Nhập thông tin học viên từ Excel");
        int count=excel.importFile(file);
        request.setAttribute("admin.changeNote","Đã nhập "+count+" học viên từ Excel");
        return Map.of("importedCount",count,"notice","Đã nhập "+count+" học viên từ Excel. Chọn Tạo tài khoản để cấp tài khoản và gửi email cho từng học viên.");
    }

    @GetMapping
    public Map<String,Object> list(@RequestParam(defaultValue="") String q,@RequestParam(defaultValue="0") int page,
                                   @RequestParam(defaultValue="10") int size) {
        return service.list(q,page,size);
    }

    @PostMapping("/{admissionId}")
    public Map<String,Object> create(@PathVariable String admissionId,@RequestBody Map<String,Object> input,HttpServletRequest request) {
        request.setAttribute("workflow.action","STUDENT_ACCOUNT_CREATE");
        request.setAttribute("workflow.label","Tạo tài khoản học viên từ hồ sơ tuyển sinh hoặc Excel");
        var result=service.create(admissionId,input);
        request.setAttribute("admin.targetId",result.get("accountId").toString());
        return result;
    }
}

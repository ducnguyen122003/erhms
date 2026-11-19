package com.erhms.workspace;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/workspace/student-payments")
public class StudentPaymentsController {
    final StudentPaymentsService service;
    public StudentPaymentsController(StudentPaymentsService service){this.service=service;}

    @GetMapping("/contracts/{id}")
    public ResponseEntity<Map<String,Object>> guide(@PathVariable String id){return privateResponse(service.guide(id));}

    @GetMapping("/contracts/{id}/qr")
    public ResponseEntity<Map<String,Object>> qr(@PathVariable String id,@RequestParam(defaultValue="TUITION") String kind,@RequestParam String amount){return privateResponse(service.qr(id,kind,amount));}

    @PostMapping("/contracts/{id}/submissions")
    public Map<String,Object> submit(@PathVariable String id,@RequestBody Map<String,Object> input,HttpServletRequest request){
        audit(request,"STUDENT_PAYMENT_SUBMIT","Học viên gửi bill chuyển khoản",id);return service.submit(id,input);
    }

    @GetMapping("/review")
    public ResponseEntity<Map<String,Object>> queue(@RequestParam(defaultValue="") String q,@RequestParam(defaultValue="PENDING") String status,@RequestParam(defaultValue="0") int page){return privateResponse(service.reviewQueue(q,status,page));}

    @GetMapping("/review/{id}")
    public ResponseEntity<Map<String,Object>> review(@PathVariable String id){return privateResponse(service.reviewDetail(id));}

    @PostMapping("/review/{id}/confirm")
    public Map<String,Object> confirm(@PathVariable String id,@RequestBody Map<String,Object> input,HttpServletRequest request){
        audit(request,"STUDENT_PAYMENT_CONFIRM","Kế toán đối chiếu sao kê và xác nhận nhận tiền",id);return service.confirm(id,input);
    }

    @PostMapping("/review/{id}/mismatch")
    public Map<String,Object> mismatch(@PathVariable String id,@RequestBody Map<String,Object> input,HttpServletRequest request){
        audit(request,"STUDENT_PAYMENT_MISMATCH","Kế toán ghi nhận bill chuyển khoản sai lệch",id);return service.mismatch(id,input);
    }

    private ResponseEntity<Map<String,Object>> privateResponse(Map<String,Object> body){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).header("X-Content-Type-Options","nosniff").body(body);}
    private void audit(HttpServletRequest request,String action,String label,String id){request.setAttribute("workflow.action",action);request.setAttribute("workflow.label",label);request.setAttribute("admin.targetId",id);}
}

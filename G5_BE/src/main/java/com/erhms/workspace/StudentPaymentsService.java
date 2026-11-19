package com.erhms.workspace;

import com.erhms.admin.service.ScreenPermissionsService;
import com.erhms.user.entity.User;
import com.erhms.user.service.CurrentUser;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static com.erhms.workspace.WorkflowData.*;

@Service
public class StudentPaymentsService {
    private static final ZoneId LOCAL_ZONE=ZoneId.of("Asia/Ho_Chi_Minh");
    private static final Set<String> RECEIPT_TYPES=Set.of("application/pdf","image/png","image/jpeg");
    final WorkflowData d;
    final CurrentUser current;
    final ScreenPermissionsService permissions;
    final FinanceWorkflows finance;
    final TrainingWorkflows training;
    final PaymentReceivingAccount receivingAccount;

    public StudentPaymentsService(WorkflowData d,CurrentUser current,ScreenPermissionsService permissions,
                                  FinanceWorkflows finance,TrainingWorkflows training,PaymentReceivingAccount receivingAccount){
        this.d=d;this.current=current;this.permissions=permissions;this.finance=finance;this.training=training;this.receivingAccount=receivingAccount;
    }

    private User access(String roleCode,String screen){
        User actor=current.get();role(actor,roleCode);permissions.require(actor,screen);return actor;
    }

    private Map<String,Object> studentContract(String id,User actor){
        var contract=d.record("contracts",id,actor);
        requireProvisioned(contract);
        return contract;
    }

    private void requireProvisioned(Map<String,Object> contract){
        if(contract.get("studentId")==null||contract.get("signatureApprovedAt")==null||contract.get("handedOverAt")==null)
            throw conflict("Hợp đồng cần được xác nhận bản ký, cấp tài khoản và bàn giao kế toán trước khi nộp tiền.");
    }

    private void requirePayable(Map<String,Object> contract){
        requireProvisioned(contract);
        if(!List.of("SIGNED","ACTIVE").contains(contract.get("status")))
            throw conflict("Chỉ tiếp nhận tiền cho hợp đồng đã ký và còn hiệu lực.");
    }

    private BigDecimal paid(UUID contract,String kind){return finance.sum(contract,kind);}
    private BigDecimal remaining(Map<String,Object> contract,String kind){
        BigDecimal due=new BigDecimal(contract.get(kind.equals("TUITION")?"tuitionDue":"depositDue").toString());
        return due.subtract(paid(uuid(contract.get("id").toString()),kind)).max(BigDecimal.ZERO);
    }

    private Map<String,Object> contractSummary(Map<String,Object> contract){
        Map<String,Object> summary=new LinkedHashMap<>();
        for(String key:List.of("id","code","studentName","courseName","status","tuitionDue","depositDue","paymentReference"))summary.put(key,contract.get(key));
        UUID id=uuid(contract.get("id").toString());
        summary.put("tuitionPaid",paid(id,"TUITION"));summary.put("depositPaid",paid(id,"DEPOSIT"));
        summary.put("tuitionRemaining",remaining(contract,"TUITION"));summary.put("depositRemaining",remaining(contract,"DEPOSIT"));
        return summary;
    }

    private Map<String,Object> paymentOption(Map<String,Object> contract,String kind){
        BigDecimal amount=remaining(contract,kind);String memo=receivingAccount.memo(contract,kind);
        Map<String,Object> option=new LinkedHashMap<>();
        option.put("kind",kind);option.put("label",kind.equals("TUITION")?"Học phí":"Cọc theo hợp đồng");
        option.put("remaining",amount);option.put("transferMemo",memo);
        option.put("qrPayload",amount.signum()>0&&List.of("SIGNED","ACTIVE").contains(contract.get("status"))?receivingAccount.qrPayload(amount,memo):null);
        return option;
    }

    @Transactional(readOnly=true)
    public Map<String,Object> guide(String id){
        User actor=access("STUDENT","TuitionDetail_Student");var contract=studentContract(id,actor);
        var submissions=d.rows("SELECT s.id,s.kind,s.amount,s.status,s.transfer_memo,s.paid_on,s.payer_name,s.declared_bank_reference,s.note,s.receipt_file_id,w.filename,s.created_at,s.review_note,s.version FROM student_payment_submissions s JOIN workflow_files w ON w.id=s.receipt_file_id WHERE s.contract_id=? AND s.submitted_by=? ORDER BY s.created_at DESC,s.id",uuid(id),actor.id);
        return Map.of("contract",contractSummary(contract),"bank",receivingAccount.details(),
                "paymentOptions",List.of(paymentOption(contract,"TUITION"),paymentOption(contract,"DEPOSIT")),
                "submissions",submissions,"canSubmit",List.of("SIGNED","ACTIVE").contains(contract.get("status")));
    }

    @Transactional(readOnly=true)
    public Map<String,Object> qr(String id,String kind,String rawAmount){
        User actor=access("STUDENT","TuitionDetail_Student");var contract=studentContract(id,actor);requirePayable(contract);
        kind=choice(Map.of("kind",kind),"kind","TUITION","DEPOSIT");
        BigDecimal amount=positiveVnd(Map.of("amount",rawAmount),"amount");
        if(amount.compareTo(remaining(contract,kind))>0)throw invalid("amount","Số tiền không được vượt nghĩa vụ còn lại đã trừ các khoản được kế toán xác nhận.");
        String memo=receivingAccount.memo(contract,kind);
        return Map.of("qrPayload",receivingAccount.qrPayload(amount,memo),"amount",amount,"transferMemo",memo);
    }

    @Transactional
    public Map<String,Object> submit(String id,Map<String,Object> input){
        User actor=access("STUDENT","TuitionDetail_Student");studentContract(id,actor);UUID contractId=uuid(id);
        d.one("SELECT id FROM training_contracts WHERE id=? FOR UPDATE",contractId);
        var contract=studentContract(id,actor);requirePayable(contract);
        String kind=choice(input,"kind","TUITION","DEPOSIT"),expected=receivingAccount.memo(contract,kind);
        String submittedMemo=text(input,"transferMemo",120,true);
        if(!submittedMemo.equals(expected))throw invalid("transferMemo","Nội dung chuyển khoản phải đúng nội dung hệ thống cấp cho hợp đồng và khoản thu này.");
        BigDecimal amount=positiveVnd(input,"amount");
        if(amount.compareTo(remaining(contract,kind))>0)throw invalid("amount","Số tiền không được vượt nghĩa vụ còn lại đã trừ các khoản được kế toán xác nhận.");
        if(d.count("SELECT COUNT(*) FROM student_payment_submissions WHERE contract_id=? AND kind=? AND status='PENDING'",contractId,kind)>0)
            throw conflict("Đã có bill của khoản thu này đang chờ kế toán đối chiếu. Vui lòng chờ kết quả trước khi gửi tiếp.");
        LocalDate paidOn=notFuture(input,"paidOn");String payer=text(input,"payerName",160,true);
        UUID receipt=uuid(input,"receiptFileId");
        var files=d.rows("SELECT id,uploaded_by,content_type,upload_screen,filename,file_size FROM workflow_files WHERE id=? FOR UPDATE",receipt);
        if(files.isEmpty())throw invalid("receiptFileId","Tải bill chuyển khoản lên trước khi gửi.");
        var file=files.get(0);
        if(((Number)file.get("uploadedBy")).longValue()!=actor.id||!"TuitionDetail_Student".equals(file.get("uploadScreen"))||!RECEIPT_TYPES.contains(file.get("contentType"))||((Number)file.get("fileSize")).longValue()<=0)
            throw invalid("receiptFileId","Chọn bill PDF, PNG hoặc JPEG do chính bạn tải lên tại phần nộp tiền theo hợp đồng.");
        if(d.count("SELECT COUNT(*) FROM student_payment_submissions WHERE receipt_file_id=?",receipt)>0)
            throw invalid("receiptFileId","Bill này đã được gửi. Không dùng lại cùng tệp cho lần nộp khác.");
        Map<String,Object> bank=receivingAccount.details();UUID submission=UUID.randomUUID();
        d.jdbc.update("INSERT INTO student_payment_submissions(id,contract_id,kind,amount,transfer_memo,paid_on,payer_name,declared_bank_reference,note,receipt_file_id,submitted_by,receiving_bank_code,receiving_bank_bin,receiving_bank_name,receiving_account_number,receiving_account_holder) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                submission,contractId,kind,amount,expected,paidOn,payer,nullable(text(input,"declaredBankReference",120,false)),nullable(text(input,"note",1000,false)),receipt,actor.id,
                bank.get("bankCode"),bank.get("bankBin"),bank.get("bankName"),bank.get("accountNumber"),bank.get("accountHolder"));
        notifyAccountants(contract,actor,"Bill chuyển khoản chờ đối chiếu","Học viên đã gửi bill "+kindLabel(kind)+" cho hợp đồng "+contract.get("code")+". Đối chiếu sao kê trước khi xác nhận nhận tiền.");
        return Map.of("id",submission.toString(),"notice","Đã gửi bill cho kế toán đối chiếu. Khoản tiền chưa được ghi nhận cho đến khi kế toán xác nhận.");
    }

    private String reviewSource(User actor){
        return " FROM student_payment_submissions s JOIN training_contracts c ON c.id=s.contract_id LEFT JOIN m_user student ON student.user_id=c.student_id JOIN workflow_files w ON w.id=s.receipt_file_id WHERE "+contractScope(actor,"c");
    }

    @Transactional(readOnly=true)
    public Map<String,Object> reviewQueue(String q,String status,int page){
        User actor=access("ACCOUNTANT","ListPayment");
        if(q.length()>200||page<0||page>100000||!Set.of("","PENDING","MISMATCH","CONFIRMED").contains(status))throw invalid("filter","Bộ lọc không hợp lệ.");
        String search="%"+q.strip().toLowerCase(Locale.ROOT).replace("!","!!").replace("%","!%").replace("_","!_")+"%";
        String source=reviewSource(actor)+" AND (?='' OR s.status=?) AND LOWER(c.code||' '||COALESCE(c.applicant_name,student.full_name,'')||' '||s.transfer_memo) LIKE ? ESCAPE '!'";
        var items=d.rows("SELECT s.id,s.contract_id,c.code AS contract_code,COALESCE(c.applicant_name,student.full_name) AS student_name,s.kind,s.amount,s.status,s.transfer_memo,s.created_at,s.version"+source+" ORDER BY s.created_at,s.id LIMIT 25 OFFSET ?",status,status,search,page*25);
        long total=d.count("SELECT COUNT(*)"+source,status,status,search);
        return Map.of("items",items,"total",total,"totalPages",(total+24)/25,"page",page,"size",25);
    }

    private Map<String,Object> reviewRecord(String id,User actor){
        return d.one("SELECT s.*,c.code AS contract_code,COALESCE(c.applicant_name,student.full_name) AS student_name,w.filename"+reviewSource(actor)+" AND s.id=?",uuid(id));
    }

    @Transactional(readOnly=true)
    public Map<String,Object> reviewDetail(String id){
        User actor=access("ACCOUNTANT","PaymentDetail");var submission=reviewRecord(id,actor);
        var contract=d.record("contracts",submission.get("contractId").toString(),actor);
        return Map.of("submission",submission,"contract",contractSummary(contract),"bank",bankSnapshot(submission),"expectedTransferMemo",submission.get("transferMemo"));
    }

    private Map<String,Object> lockReview(String id,Map<String,Object> input,User actor){
        var before=reviewRecord(id,actor);UUID contract=uuid(before.get("contractId").toString());
        finance.claim(contract,actor);d.lock("student_payment_submissions",uuid(id),input);
        var submission=reviewRecord(id,actor);requireProvisioned(d.record("contracts",contract.toString(),actor));
        if(!"PENDING".equals(submission.get("status")))throw conflict("Chỉ đối chiếu bill đang chờ xác nhận. Hãy tải lại để xem kết quả mới nhất.");
        return submission;
    }

    @Transactional
    public Map<String,Object> confirm(String id,Map<String,Object> input){
        User actor=access("ACCOUNTANT","PaymentDetail");var submission=lockReview(id,input,actor);
        UUID submissionId=uuid(id),contractId=uuid(submission.get("contractId").toString());
        var contract=d.record("contracts",contractId.toString(),actor);
        requirePayable(contract);
        BigDecimal amount=positiveVnd(input,"statementAmount"),claimed=new BigDecimal(submission.get("amount").toString());
        if(amount.compareTo(claimed)!=0)throw invalid("statementAmount","Số tiền trên sao kê phải khớp số tiền của bill đang đối chiếu.");
        String statementMemo=text(input,"statementMemo",1000,true),expected=submission.get("transferMemo").toString();
        if(!normalizedMemo(statementMemo).equals(normalizedMemo(expected)))throw invalid("statementMemo","Nội dung chuyển khoản trên sao kê chưa khớp nội dung được cấp cho khoản thu này.");
        String reference=text(input,"bankReference",120,true),reviewNote=text(input,"changeNote",1000,true);
        LocalDate statementDate=notFuture(input,"statementDate");
        if(!flag(input,"identityVerified")||!flag(input,"amountVerified")||!flag(input,"referenceVerified"))throw invalid("verified","Xác nhận đã đối chiếu người học, số tiền và mã giao dịch trên sao kê.");
        if(amount.compareTo(remaining(contract,submission.get("kind").toString()))>0)throw conflict("Số tiền vượt nghĩa vụ còn lại. Cần rà soát bill và các khoản đã xác nhận.");
        var existing=d.rows("SELECT * FROM financial_transactions WHERE bank_reference=?",reference);
        UUID transaction;
        if(existing.isEmpty()){
            transaction=UUID.randomUUID();
            d.jdbc.update("INSERT INTO financial_transactions(id,contract_id,kind,amount,status,bank_reference,payer_name,bank_name,memo,evidence) VALUES (?,?,?,?,'PENDING',?,?,?,?,?)",
                    transaction,contractId,submission.get("kind"),amount,reference,submission.get("payerName"),submission.get("receivingBankName"),statementMemo,"file:"+submission.get("receiptFileId")+":"+submission.get("filename"));
        }else{
            var imported=existing.get(0);transaction=uuid(imported.get("id").toString());
            if(!contractId.toString().equals(imported.get("contractId"))||!submission.get("kind").equals(imported.get("kind"))
                    ||!"PENDING".equals(imported.get("status"))||amount.compareTo(new BigDecimal(imported.get("amount").toString()))!=0
                    ||!normalizedMemo(Objects.toString(imported.get("memo"),"")).equals(normalizedMemo(expected)))
                throw conflict("Mã giao dịch đã tồn tại nhưng không khớp bill hoặc đã được xác nhận. Không ghi nhận khoản thu lần thứ hai.");
            if(d.count("SELECT COUNT(*) FROM student_payment_submissions WHERE transaction_id=?",transaction)>0)
                throw conflict("Giao dịch này đã đối chiếu với bill khác.");
        }
        var ledger=d.one("SELECT version FROM financial_transactions WHERE id=?",transaction);
        Map<String,Object> verification=new LinkedHashMap<>(input);verification.put("version",ledger.get("version"));
        finance.payment(transaction.toString(),verification,actor,"confirm");
        d.jdbc.update("UPDATE student_payment_submissions SET status='CONFIRMED',transaction_id=?,statement_bank_reference=?,statement_amount=?,statement_memo=?,statement_date=?,reviewed_by=?,reviewed_at=CURRENT_TIMESTAMP,review_note=?,version=version+1 WHERE id=?",
                transaction,reference,amount,statementMemo,statementDate,actor.id,reviewNote,submissionId);
        return Map.of("id",id,"transactionId",transaction.toString(),"notice","Đã đối chiếu sao kê và xác nhận nhận tiền. Học phí chỉ được tính từ khoản đã xác nhận.");
    }

    @Transactional
    public Map<String,Object> mismatch(String id,Map<String,Object> input){
        User actor=access("ACCOUNTANT","PaymentDetail");var submission=lockReview(id,input,actor);String note=text(input,"reviewNote",1000,true);
        d.jdbc.update("UPDATE student_payment_submissions SET status='MISMATCH',reviewed_by=?,reviewed_at=CURRENT_TIMESTAMP,review_note=?,version=version+1 WHERE id=?",actor.id,note,uuid(id));
        training.notify(((Number)submission.get("submittedBy")).longValue(),"Bill chuyển khoản cần kiểm tra","Bill của hợp đồng "+submission.get("contractCode")+" chưa được xác nhận: "+note,"TuitionDetail_Student",submission.get("contractId").toString(),actor);
        return Map.of("id",id,"notice","Đã ghi nhận sai lệch và thông báo cho học viên. Khoản tiền chưa được cộng vào số đã nộp.");
    }

    private Map<String,Object> bankSnapshot(Map<String,Object> row){
        return Map.of("bankCode",row.get("receivingBankCode"),"bankBin",row.get("receivingBankBin"),"bankName",row.get("receivingBankName"),"accountNumber",row.get("receivingAccountNumber"),"accountHolder",row.get("receivingAccountHolder"));
    }

    private BigDecimal positiveVnd(Map<String,Object> input,String field){
        BigDecimal amount=money(input,field);
        if(amount.signum()<=0||amount.stripTrailingZeros().scale()>0)throw invalid(field,"Nhập số tiền Việt Nam đồng là số nguyên lớn hơn 0.");
        return amount;
    }
    private LocalDate notFuture(Map<String,Object> input,String field){
        LocalDate date=date(input,field,true);if(date.isAfter(LocalDate.now(LOCAL_ZONE)))throw invalid(field,"Ngày không được ở tương lai.");return date;
    }
    private static String normalizedMemo(String text){return text.strip().replaceAll("(?U)\\s+"," ").toUpperCase(Locale.ROOT);}
    private static String nullable(String text){return text.isEmpty()?null:text;}
    private static String kindLabel(String kind){return kind.equals("TUITION")?"học phí":"cọc theo hợp đồng";}
    private void notifyAccountants(Map<String,Object> contract,User actor,String title,String content){
        if(contract.get("accountantId") instanceof Number accountant){training.notify(accountant.longValue(),title,content,"ListPayment",null,actor);return;}
        for(var accountant:d.rows("SELECT u.user_id FROM m_user u WHERE u.role_id=4 AND u.account_enabled=TRUE AND u.account_locked=FALSE AND EXISTS(SELECT 1 FROM role_function rf WHERE rf.role_id=4 AND rf.function_code='ListPayment')"))
            training.notify(((Number)accountant.get("userId")).longValue(),title,content,"ListPayment",null,actor);
    }
}

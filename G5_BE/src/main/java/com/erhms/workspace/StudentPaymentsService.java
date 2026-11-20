package com.erhms.workspace;

import com.erhms.admin.service.ScreenPermissionsService;
import com.erhms.user.StudentCode;
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
        if(kind.equals("COMBINED"))return remaining(contract,"TUITION").add(remaining(contract,"DEPOSIT"));
        BigDecimal due=new BigDecimal(contract.get(kind.equals("TUITION")?"tuitionDue":"depositDue").toString());
        return due.subtract(paid(uuid(contract.get("id").toString()),kind)).max(BigDecimal.ZERO);
    }

    private Map<String,Object> contractSummary(Map<String,Object> contract){
        Map<String,Object> summary=new LinkedHashMap<>();
        for(String key:List.of("id","code","studentName","accountStudentName","studentCode","courseName","status","tuitionDue","depositDue","paymentReference"))summary.put(key,contract.get(key));
        UUID id=uuid(contract.get("id").toString());
        summary.put("tuitionPaid",paid(id,"TUITION"));summary.put("depositPaid",paid(id,"DEPOSIT"));
        summary.put("tuitionRemaining",remaining(contract,"TUITION"));summary.put("depositRemaining",remaining(contract,"DEPOSIT"));
        return summary;
    }

    private Map<String,Object> paymentOption(Map<String,Object> contract,String kind){
        BigDecimal amount=remaining(contract,kind);String memo=receivingAccount.memo(contract,kind);
        Map<String,Object> option=new LinkedHashMap<>();
        option.put("kind",kind);option.put("label",kind.equals("COMBINED")?"Học phí và cọc theo hợp đồng":kind.equals("TUITION")?"Học phí":"Cọc theo hợp đồng");
        option.put("remaining",amount);option.put("transferMemo",memo);
        if(kind.equals("COMBINED")){option.put("tuitionRemaining",remaining(contract,"TUITION"));option.put("depositRemaining",remaining(contract,"DEPOSIT"));}
        option.put("qrPayload",amount.signum()>0&&List.of("SIGNED","ACTIVE").contains(contract.get("status"))?receivingAccount.qrPayload(amount,memo):null);
        return option;
    }

    @Transactional(readOnly=true)
    public Map<String,Object> guide(String id){
        User actor=access("STUDENT","TuitionDetail_Student");var contract=studentContract(id,actor);
        var submissions=d.rows("SELECT s.id,s.kind,s.amount,s.tuition_amount,s.deposit_amount,s.status,s.transfer_memo,s.paid_on,s.payer_name,s.declared_bank_reference,s.note,s.receipt_file_id,w.filename,s.created_at,s.review_note,s.version FROM student_payment_submissions s JOIN workflow_files w ON w.id=s.receipt_file_id WHERE s.contract_id=? AND s.submitted_by=? ORDER BY s.created_at DESC,s.id",uuid(id),actor.id);
        var options=new ArrayList<Map<String,Object>>();options.add(paymentOption(contract,"TUITION"));options.add(paymentOption(contract,"DEPOSIT"));
        if(remaining(contract,"TUITION").signum()>0&&remaining(contract,"DEPOSIT").signum()>0)options.add(paymentOption(contract,"COMBINED"));
        return Map.of("contract",contractSummary(contract),"bank",receivingAccount.details(),
                "paymentOptions",options,
                "submissions",submissions,"canSubmit",List.of("SIGNED","ACTIVE").contains(contract.get("status")));
    }

    @Transactional(readOnly=true)
    public Map<String,Object> qr(String id,String kind,String rawAmount){
        User actor=access("STUDENT","TuitionDetail_Student");var contract=studentContract(id,actor);requirePayable(contract);
        kind=choice(Map.of("kind",kind),"kind","TUITION","DEPOSIT","COMBINED");
        if(kind.equals("COMBINED")&&(remaining(contract,"TUITION").signum()<=0||remaining(contract,"DEPOSIT").signum()<=0))throw invalid("kind","Chỉ chọn nộp gộp khi cả học phí và tiền cọc đều còn nghĩa vụ phải nộp.");
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
        Allocation allocation=allocation(input);String kind=allocation.kind(),expected=receivingAccount.memo(contract,kind);
        String submittedMemo=text(input,"transferMemo",120,true);
        if(!submittedMemo.equals(expected))throw invalid("transferMemo","Nội dung chuyển khoản phải đúng nội dung hệ thống cấp cho hợp đồng và khoản thu này.");
        BigDecimal amount=allocation.total();checkRemaining(contract,allocation,false);
        String overlap=kind.equals("COMBINED")?"kind IN ('TUITION','DEPOSIT','COMBINED')":kind.equals("TUITION")?"kind IN ('TUITION','COMBINED')":"kind IN ('DEPOSIT','COMBINED')";
        if(d.count("SELECT COUNT(*) FROM student_payment_submissions WHERE contract_id=? AND status='PENDING' AND "+overlap,contractId)>0)
            throw conflict("Đã có bill của một trong các khoản đã chọn đang chờ kế toán đối chiếu. Vui lòng chờ kết quả trước khi gửi tiếp.");
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
        d.jdbc.update("INSERT INTO student_payment_submissions(id,contract_id,kind,amount,tuition_amount,deposit_amount,transfer_memo,paid_on,payer_name,declared_bank_reference,note,receipt_file_id,submitted_by,receiving_bank_code,receiving_bank_bin,receiving_bank_name,receiving_account_number,receiving_account_holder) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                submission,contractId,kind,amount,allocation.tuition(),allocation.deposit(),expected,paidOn,payer,nullable(text(input,"declaredBankReference",120,false)),nullable(text(input,"note",1000,false)),receipt,actor.id,
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
        String source=reviewSource(actor)+" AND (?='' OR s.status=?) AND LOWER(c.code||' '||COALESCE(c.applicant_name,student.full_name,'')||' '||COALESCE("+StudentCode.sql("c.student_id")+",'')||' '||s.transfer_memo) LIKE ? ESCAPE '!'";
        var items=d.rows("SELECT s.id,s.contract_id,c.code AS contract_code,COALESCE(c.applicant_name,student.full_name) AS student_name,"+StudentCode.sql("c.student_id")+" AS student_code,s.kind,s.amount,s.tuition_amount,s.deposit_amount,s.status,s.transfer_memo,s.created_at,s.version"+source+" ORDER BY s.created_at,s.id LIMIT 25 OFFSET ?",status,status,search,page*25);
        long total=d.count("SELECT COUNT(*)"+source,status,status,search);
        return Map.of("items",items,"total",total,"totalPages",(total+24)/25,"page",page,"size",25);
    }

    private Map<String,Object> reviewRecord(String id,User actor){
        return d.one("SELECT s.*,c.code AS contract_code,COALESCE(c.applicant_name,student.full_name) AS student_name,"+StudentCode.sql("c.student_id")+" AS student_code,w.filename"+reviewSource(actor)+" AND s.id=?",uuid(id));
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
        Allocation allocation=new Allocation(submission.get("kind").toString(),new BigDecimal(submission.get("amount").toString()),new BigDecimal(submission.get("tuitionAmount").toString()),new BigDecimal(submission.get("depositAmount").toString()));
        BigDecimal amount=positiveVnd(input,"statementAmount");
        if(amount.compareTo(allocation.total())!=0)throw invalid("statementAmount","Số tiền trên sao kê phải khớp tổng số tiền của bill đang đối chiếu.");
        String statementMemo=text(input,"statementMemo",1000,true),expected=submission.get("transferMemo").toString();
        if(!normalizedMemo(statementMemo).equals(normalizedMemo(expected)))throw invalid("statementMemo","Nội dung chuyển khoản trên sao kê chưa khớp nội dung được cấp cho khoản thu này.");
        String reference=text(input,"bankReference",120,true),reviewNote=text(input,"changeNote",1000,true);
        LocalDate statementDate=notFuture(input,"statementDate");
        if(!flag(input,"identityVerified")||!flag(input,"amountVerified")||!flag(input,"referenceVerified"))throw invalid("verified","Xác nhận đã đối chiếu người học, số tiền và mã giao dịch trên sao kê.");
        checkRemaining(contract,allocation,true);
        var existing=d.rows("SELECT * FROM financial_transactions WHERE bank_reference=?",reference);
        UUID transaction;String evidence="file:"+submission.get("receiptFileId")+":"+submission.get("filename");
        if(existing.isEmpty()){
            transaction=UUID.randomUUID();
            d.jdbc.update("INSERT INTO financial_transactions(id,contract_id,kind,amount,status,bank_reference,payer_name,bank_name,memo,evidence) VALUES (?,?,?,?,'PENDING',?,?,?,?,?)",
                    transaction,contractId,allocation.primaryKind(),allocation.primaryAmount(),reference,submission.get("payerName"),submission.get("receivingBankName"),statementMemo,evidence);
        }else{
            var candidate=existing.get(0);transaction=uuid(candidate.get("id").toString());
            if(!contractId.toString().equals(candidate.get("contractId")))throw conflict("Mã giao dịch đã được dùng cho hợp đồng khác.");
            d.lock("financial_transactions",transaction,Map.of("version",candidate.get("version")));
            var imported=d.one("SELECT * FROM financial_transactions WHERE id=?",transaction);
            if(!contractId.toString().equals(imported.get("contractId"))||!allocation.primaryKind().equals(imported.get("kind"))
                    ||!"PENDING".equals(imported.get("status"))||amount.compareTo(new BigDecimal(imported.get("amount").toString()))!=0
                    ||!normalizedMemo(Objects.toString(imported.get("memo"),"")).equals(normalizedMemo(expected)))
                throw conflict("Mã giao dịch đã tồn tại nhưng không khớp bill hoặc đã được xác nhận. Không ghi nhận khoản thu lần thứ hai.");
            if(d.count("SELECT COUNT(*) FROM student_payment_submissions WHERE transaction_id=? OR deposit_transaction_id=?",transaction,transaction)>0)
                throw conflict("Giao dịch này đã đối chiếu với bill khác.");
            if(imported.get("evidence")!=null&&!imported.get("evidence").toString().isBlank())evidence=imported.get("evidence").toString();
            // The bank imported one total. Retain its real reference on the tuition
            // allocation, while the submission preserves the original statement total.
            if(allocation.combined())d.jdbc.update("UPDATE financial_transactions SET amount=?,version=version+1 WHERE id=?",allocation.tuition(),transaction);
        }
        UUID depositTransaction=null;
        if(allocation.combined()){
            depositTransaction=UUID.randomUUID();
            d.jdbc.update("INSERT INTO financial_transactions(id,contract_id,kind,amount,status,payer_name,bank_name,memo,evidence) VALUES (?,?,'DEPOSIT',?,'PENDING',?,?,?,?)",
                    depositTransaction,contractId,allocation.deposit(),submission.get("payerName"),submission.get("receivingBankName"),statementMemo,evidence);
        }
        var ledger=d.one("SELECT version FROM financial_transactions WHERE id=?",transaction);
        Map<String,Object> verification=new LinkedHashMap<>(input);verification.put("version",ledger.get("version"));
        finance.payment(transaction.toString(),verification,actor,"confirm");
        // This secondary allocation has no independent bank reference. It can only
        // be posted here, after verifying the one parent transfer under the same
        // contract lock; it is linked to that verified submission in this transaction.
        if(depositTransaction!=null)d.jdbc.update("UPDATE financial_transactions SET status='CONFIRMED',confirmed_by=?,confirmed_at=CURRENT_TIMESTAMP,version=version+1 WHERE id=?",actor.id,depositTransaction);
        d.jdbc.update("UPDATE student_payment_submissions SET status='CONFIRMED',transaction_id=?,deposit_transaction_id=?,statement_bank_reference=?,statement_amount=?,statement_memo=?,statement_date=?,reviewed_by=?,reviewed_at=CURRENT_TIMESTAMP,review_note=?,version=version+1 WHERE id=?",
                transaction,depositTransaction,reference,amount,statementMemo,statementDate,actor.id,reviewNote,submissionId);
        if(depositTransaction!=null)training.notify(((Number)submission.get("submittedBy")).longValue(),"Đã xác nhận tiền cọc","Tiền cọc trong lần chuyển khoản gộp của hợp đồng "+submission.get("contractCode")+" đã được kế toán đối chiếu và xác nhận.","TuitionDetail_Student",contractId.toString(),actor);
        var result=new LinkedHashMap<String,Object>();result.put("id",id);result.put("transactionId",transaction.toString());
        if(depositTransaction!=null)result.put("depositTransactionId",depositTransaction.toString());
        result.put("notice","Đã đối chiếu sao kê và xác nhận nhận tiền. Học phí chỉ được tính từ khoản đã xác nhận.");return result;
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
    private record Allocation(String kind,BigDecimal total,BigDecimal tuition,BigDecimal deposit){
        boolean combined(){return kind.equals("COMBINED");}
        String primaryKind(){return combined()?"TUITION":kind;}
        BigDecimal primaryAmount(){return combined()?tuition:total;}
    }
    private Allocation allocation(Map<String,Object> input){
        String kind=choice(input,"kind","TUITION","DEPOSIT","COMBINED");BigDecimal total=positiveVnd(input,"amount");
        if(kind.equals("TUITION"))return new Allocation(kind,total,total,BigDecimal.ZERO);
        if(kind.equals("DEPOSIT"))return new Allocation(kind,total,BigDecimal.ZERO,total);
        BigDecimal tuition=positiveVnd(input,"tuitionAmount"),deposit=positiveVnd(input,"depositAmount");
        if(tuition.add(deposit).compareTo(total)!=0)throw invalid("amount","Tổng tiền chuyển khoản phải bằng học phí cộng tiền cọc đã chọn.");
        return new Allocation(kind,total,tuition,deposit);
    }
    private void checkRemaining(Map<String,Object> contract,Allocation allocation,boolean reviewing){
        if(allocation.tuition().compareTo(remaining(contract,"TUITION"))>0){
            if(reviewing)throw conflict("Phần học phí vượt nghĩa vụ còn lại. Cần rà soát bill và các khoản đã xác nhận.");
            throw invalid(allocation.combined()?"tuitionAmount":"amount","Số tiền học phí không được vượt nghĩa vụ học phí còn lại.");
        }
        if(allocation.deposit().compareTo(remaining(contract,"DEPOSIT"))>0){
            if(reviewing)throw conflict("Phần tiền cọc vượt nghĩa vụ còn lại. Cần rà soát bill và các khoản đã xác nhận.");
            throw invalid(allocation.combined()?"depositAmount":"amount","Số tiền cọc không được vượt nghĩa vụ tiền cọc còn lại.");
        }
    }
    private LocalDate notFuture(Map<String,Object> input,String field){
        LocalDate date=date(input,field,true);if(date.isAfter(LocalDate.now(LOCAL_ZONE)))throw invalid(field,"Ngày không được ở tương lai.");return date;
    }
    private static String normalizedMemo(String text){return text.strip().replaceAll("(?U)\\s+"," ").toUpperCase(Locale.ROOT);}
    private static String nullable(String text){return text.isEmpty()?null:text;}
    private static String kindLabel(String kind){return kind.equals("COMBINED")?"học phí và cọc theo hợp đồng":kind.equals("TUITION")?"học phí":"cọc theo hợp đồng";}
    private void notifyAccountants(Map<String,Object> contract,User actor,String title,String content){
        if(contract.get("accountantId") instanceof Number accountant){training.notify(accountant.longValue(),title,content,"ListPayment",null,actor);return;}
        for(var accountant:d.rows("SELECT u.user_id FROM m_user u WHERE u.role_id=4 AND u.account_enabled=TRUE AND u.account_locked=FALSE AND EXISTS(SELECT 1 FROM role_function rf WHERE rf.role_id=4 AND rf.function_code='ListPayment')"))
            training.notify(((Number)accountant.get("userId")).longValue(),title,content,"ListPayment",null,actor);
    }
}

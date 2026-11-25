package com.erhms.workspace;

import com.erhms.user.entity.User;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.*;
import static com.erhms.workspace.WorkflowData.*;
import static com.erhms.workspace.TrainingWorkflows.saved;

@Service
public class FinanceWorkflows {
    final WorkflowData d;final TrainingWorkflows training;final MasterDataService masters;
    public FinanceWorkflows(WorkflowData d,TrainingWorkflows training,MasterDataService masters){this.d=d;this.training=training;this.masters=masters;}
    BigDecimal sum(UUID id,String kind){return d.jdbc.queryForObject("SELECT COALESCE(SUM(amount),0) FROM financial_transactions WHERE contract_id=? AND kind=? AND status='CONFIRMED'",BigDecimal.class,id,kind);}
    void claim(UUID contract,User user){
        d.one("SELECT id FROM training_contracts WHERE id=? FOR UPDATE",contract);d.record("contracts",contract.toString(),user);
        d.jdbc.update("UPDATE training_contracts SET accountant_id=? WHERE id=? AND accountant_id IS NULL",user.id,contract);
    }
    public Map<String,Object> importPayment(Map<String,Object> p,User u){
        role(u,"ACCOUNTANT");UUID contract=uuid(p,"contractId");d.record("contracts",contract.toString(),u);claim(contract,u);
        String reference=text(p,"bankReference",120,true);var duplicate=d.rows("SELECT id FROM financial_transactions WHERE bank_reference=?",reference);
        if(!duplicate.isEmpty()){d.record("payments",duplicate.get(0).get("id").toString(),u);return Map.of("screen","PaymentDetail_Duplicate","id",duplicate.get(0).get("id"),"notice","Giao dịch đã có trong hệ thống; mở bản ghi gốc, không ghi nhận lần thu thứ hai.");}
        String kind=choice(p,"kind","TUITION","DEPOSIT");BigDecimal amount=money(p,"amount");if(amount.signum()<=0)throw invalid("amount","Số tiền phải lớn hơn 0");UUID id=UUID.randomUUID();
        d.jdbc.update("INSERT INTO financial_transactions(id,contract_id,kind,amount,status,bank_reference,payer_name,bank_name,memo,evidence,bank_id) VALUES (?,?,?,?,'PENDING',?,?,?,?,?,?)",id,contract,kind,amount,reference,text(p,"payerName",160,true),masters.requireActive("banks",p,"bankId").get("name").toString(),text(p,"memo",1000,true),text(p,"evidence",1000,true),uuid(p,"bankId"));
        return saved("PaymentDetail",id);
    }
    public Map<String,Object> payment(String id,Map<String,Object> p,User u,String action){
        role(u,"ACCOUNTANT");UUID key=uuid(id);var transaction=d.record("payments",id,u);UUID contract=uuid(transaction.get("contractId").toString());claim(contract,u);d.lock("financial_transactions",key,p);transaction=d.record("payments",id,u);
        if("CONFIRMED".equals(transaction.get("status")))throw conflict("Giao dịch này đã được xác nhận; không ghi nhận lần thứ hai");
        if(action.equals("mismatch")){d.jdbc.update("UPDATE financial_transactions SET status='MISMATCH',memo=?,version=version+1 WHERE id=?",text(p,"memo",1000,true),key);return saved("PaymentDetail_Mismatch",key);}
        if(action.equals("resolve")){if(!"MISMATCH".equals(transaction.get("status")))throw conflict("Giao dịch không ở trạng thái sai lệch");d.jdbc.update("UPDATE financial_transactions SET status='PENDING',memo=?,version=version+1 WHERE id=?",text(p,"memo",1000,true),key);return saved("PaymentDetail",key);}
        if(!"PENDING".equals(transaction.get("status")))throw conflict("Làm rõ sai lệch trước khi xác nhận giao dịch");
        var ct=d.record("contracts",contract.toString(),u);
        if(!List.of("SIGNED","ACTIVE").contains(ct.get("status")))throw conflict("Hợp đồng cần được ký và còn hiệu lực trước khi xác nhận tiền");
        long studentId=contractStudent(ct);
        String kind=transaction.get("kind").toString();if(!List.of("TUITION","DEPOSIT").contains(kind))throw conflict("Khoản hoàn cọc/bồi thường phải xử lý ở nghiệp vụ quyết toán");
        String reference=transaction.get("bankReference")==null?"":transaction.get("bankReference").toString();if(reference.isBlank())throw invalid("bankReference","Giao dịch chưa có mã ngân hàng để kiểm tra trùng");
        if(!flag(p,"identityVerified")||!flag(p,"amountVerified")||!flag(p,"referenceVerified"))throw invalid("verified","Xác nhận đã đối chiếu hợp đồng, số tiền và mã giao dịch");
        text(p,"changeNote",1000,true);BigDecimal due=new BigDecimal(ct.get(kind.equals("TUITION")?"tuitionDue":"depositDue").toString()),amount=new BigDecimal(transaction.get("amount").toString());
        if(amount.compareTo(due.subtract(sum(contract,kind)))>0)throw conflict("Số tiền vượt nghĩa vụ còn lại. Cần làm rõ sai lệch trước khi xác nhận.");
        if(d.count("SELECT COUNT(*) FROM financial_transactions WHERE bank_reference=? AND id<>? AND status='CONFIRMED'",reference,key)>0)throw conflict("Mã giao dịch đã được ghi nhận ở bản ghi khác");
        d.jdbc.update("UPDATE financial_transactions SET status='CONFIRMED',confirmed_by=?,confirmed_at=CURRENT_TIMESTAMP,version=version+1 WHERE id=?",u.id,key);
        String enrollmentNotice="";
        if(kind.equals("TUITION")&&sum(contract,kind).compareTo(due)>=0){
            d.jdbc.update("UPDATE training_contracts SET status='ACTIVE',version=version+1 WHERE id=?",contract);
            enrollmentNotice=training.enrollAfterTuition(contract);
        }
        String paymentNotice="Khoản "+(kind.equals("TUITION")?"học phí":"cọc thiết bị")+" của hợp đồng "+ct.get("code")+" đã được kế toán xác nhận.";
        training.notify(studentId,"Đã xác nhận thanh toán",withEnrollmentNotice(paymentNotice,enrollmentNotice),"TuitionDetail_Student",contract.toString(),u);
        return Map.of("screen","PaymentDetail","id",id,"notice",withEnrollmentNotice("Đã xác nhận nhận tiền.",enrollmentNotice),"enrollmentNotice",enrollmentNotice);
    }
    public Map<String,Object> instruction(String id,Map<String,Object> p,User u){
        role(u,"ACCOUNTANT");UUID ct=uuid(id);d.record("contracts",id,u);claim(ct,u);String content=text(p,"content",4000,true);
        boolean exists=d.count("SELECT COUNT(*) FROM payment_instructions WHERE contract_id=?",ct)>0;
        if(exists){d.lock("payment_instructions",ct,p);d.jdbc.update("UPDATE payment_instructions SET content=?,created_by=?,updated_at=CURRENT_TIMESTAMP,version=version+1 WHERE contract_id=?",content,u.id,ct);}
        else d.jdbc.update("INSERT INTO payment_instructions(contract_id,content,created_by,updated_at) VALUES (?,?,?,CURRENT_TIMESTAMP)",ct,content,u.id);
        var c=d.record("contracts",id,u);training.notify(((Number)c.get("studentId")).longValue(),"Hướng dẫn thanh toán","Hướng dẫn thanh toán cho hợp đồng "+c.get("code")+" đã được cập nhật.","PaymentInstruction",id,u);
        return saved("TuitionDetail",ct);
    }
    public Map<String,Object> activateZeroTuition(String id,Map<String,Object> p,User user){
        role(user,"ACCOUNTANT");UUID key=uuid(id);d.record("contracts",id,user);claim(key,user);d.lock("training_contracts",key,p);var contract=d.record("contracts",id,user);
        if(!"SIGNED".equals(contract.get("status"))||new BigDecimal(contract.get("tuitionDue").toString()).signum()!=0)throw conflict("Chỉ xác nhận miễn học phí cho hợp đồng đã ký có nghĩa vụ học phí bằng 0");
        long studentId=contractStudent(contract);
        text(p,"changeNote",1000,true);
        d.jdbc.update("UPDATE training_contracts SET status='ACTIVE',version=version+1 WHERE id=?",key);
        String notice=withEnrollmentNotice("Đã xác nhận hợp đồng miễn học phí.",training.enrollAfterTuition(key));
        training.notify(studentId,"Đã xác nhận miễn học phí",notice,"TuitionDetail_Student",id,user);
        return Map.of("screen","TuitionDetail","id",id,"notice",notice);
    }

    private long contractStudent(Map<String,Object> contract){
        if(contract.get("studentId") instanceof Number student)return student.longValue();
        throw conflict("Cần liên kết tài khoản học viên với hợp đồng trước khi xác nhận học phí.");
    }

    static String withEnrollmentNotice(String notice,String enrollmentNotice){
        return enrollmentNotice.isEmpty()?notice:notice+" "+enrollmentNotice;
    }

    public Map<String,Object> requestRefund(String screen,String id,Map<String,Object> input,User actor){
        role(actor,"IT_SUPPORT");
        var loan=d.record("loans",id,actor);
        if(loan.get("contractId")==null)throw conflict("Biên bản chưa liên kết hợp đồng để lập phiếu hoàn cọc");
        UUID contractId=uuid(loan.get("contractId").toString());
        var contract=d.one("SELECT id,code,status,student_id,accountant_id,version FROM training_contracts WHERE id=? FOR UPDATE",contractId);
        if(number(input,"contractVersion",0,Long.MAX_VALUE)!=((Number)contract.get("version")).longValue())
            throw conflict("Phiếu hoặc hợp đồng vừa thay đổi. Tải lại trước khi lập phiếu.");
        var context=refundContext(contract);
        if(!Boolean.TRUE.equals(context.get("canRequest")))throw conflict(context.get("reason").toString());
        if(d.record("loans",id,actor).get("returnedAt")==null)throw conflict("Xác nhận đã nhận lại thiết bị trước khi lập phiếu");

        int percent=(int)number(input,"refundPercent",0,100);
        String reason=text(input,"reason",1000,true),recipient=text(input,"recipientName",160,true);
        String bank=text(input,"bankName",120,true),account=text(input,"accountNumber",34,true);
        if(!account.matches("[0-9]{4,34}"))throw invalid("accountNumber","Nhập số tài khoản ngân hàng gồm 4 đến 34 chữ số");
        BigDecimal deposit=(BigDecimal)context.get("depositAmount");
        BigDecimal refund=percent==100?deposit:deposit.multiply(BigDecimal.valueOf(percent,2)).setScale(0,RoundingMode.HALF_UP).min(deposit);
        if(approvedDepositOffsets(contractId).compareTo(deposit.subtract(refund))>0)
            throw invalid("refundPercent","Tỷ lệ hoàn phải chừa đủ khoản bồi thường đã được duyệt khấu trừ cọc");
        int updated=d.jdbc.update("""
                UPDATE deposit_refund_requests SET refund_percent=?,reason=?,recipient_name=?,bank_name=?,account_number=?,
                    deposit_amount=?,refund_amount=?,requested_by=?,updated_at=CURRENT_TIMESTAMP WHERE contract_id=?
                """,percent,reason,recipient,bank,account,deposit,refund,actor.id,contractId);
        if(updated==0)d.jdbc.update("""
                INSERT INTO deposit_refund_requests(id,contract_id,refund_percent,reason,recipient_name,bank_name,account_number,
                    deposit_amount,refund_amount,requested_by) VALUES (?,?,?,?,?,?,?,?,?,?)
                """,UUID.randomUUID(),contractId,percent,reason,recipient,bank,account,deposit,refund,actor.id);
        d.jdbc.update("UPDATE training_contracts SET version=version+1 WHERE id=?",contractId);

        List<Long> recipients=contract.get("accountantId") instanceof Number assigned?List.of(assigned.longValue())
                :d.jdbc.queryForList("""
                        SELECT u.user_id FROM m_user u JOIN role_function f ON f.role_id=u.role_id AND f.function_code='DepositRefund'
                        WHERE u.role_id=4 AND u.account_enabled=TRUE AND u.account_locked=FALSE
                        """,Long.class);
        for(long accountant:recipients)training.notify(accountant,"Phiếu đề nghị hoàn tiền cọc",
                "IT đã "+(updated==0?"gửi":"cập nhật")+" phiếu hoàn cọc "+percent+"% cho hợp đồng "+contract.get("code")+".",
                "DepositRefund",contractId.toString(),actor);
        return Map.of("screen",screen,"id",id,"notice","Đã gửi phiếu đề nghị hoàn cọc cho kế toán.");
    }

    void attachRefundReview(Map<String,Object> related,Map<String,Object> contract){
        UUID id=uuid(contract.get("id").toString());
        related.put("refundContext",refundContext(contract));
        related.put("refundReview",d.rows("""
                SELECT id,refund_percent,reason,recipient_name,bank_name,account_number,deposit_amount,refund_amount,created_at,updated_at
                FROM deposit_refund_requests WHERE contract_id=?
                """,id));
    }

    private Map<String,Object> refundContext(Map<String,Object> contract){
        UUID id=uuid(contract.get("id").toString());
        BigDecimal deposit=sum(id,"DEPOSIT");
        boolean settled=d.count("SELECT COUNT(*) FROM deposit_settlements WHERE contract_id=?",id)>0||sum(id,"REFUND").signum()>0;
        boolean hasLoans=d.count("SELECT COUNT(*) FROM equipment_loans WHERE contract_id=?",id)>0;
        var reviews=d.rows("SELECT deposit_amount,refund_amount FROM deposit_refund_requests WHERE contract_id=?",id);
        String reason="";
        if(settled)reason="Hợp đồng đã quyết toán cọc";
        else if(deposit.signum()<=0)reason="Chưa có tiền cọc được kế toán xác nhận";
        else if(d.count("SELECT COUNT(*) FROM equipment_loans WHERE returned_at IS NULL AND (contract_id=? OR (contract_id IS NULL AND student_id=?))",id,contract.get("studentId"))>0)
            reason="Chờ IT xác nhận thu hồi tất cả thiết bị";
        else if(hasLoans&&(!"ACTIVE".equals(contract.get("status"))||d.count("""
                SELECT COUNT(*) FROM training_enrollments WHERE contract_id=? AND closed_at IS NULL
                  AND (academic_completed_at IS NOT NULL OR academic_failed_at IS NOT NULL OR status='WITHDRAWN')
                """,id)==0))reason="Hồ sơ chưa được xác nhận kết quả khóa học hoặc ghi nhận nghỉ giữa chừng";

        boolean canRequest=hasLoans&&reason.isEmpty();
        if(hasLoans&&reason.isEmpty()){
            if(reviews.isEmpty())reason="Chờ IT lập phiếu đề nghị hoàn cọc";
            else if(deposit.compareTo(new BigDecimal(reviews.get(0).get("depositAmount").toString()))!=0)
                reason="Tiền cọc đã thay đổi. Cần IT cập nhật lại phiếu hoàn cọc.";
            else if(approvedDepositOffsets(id).compareTo(deposit.subtract(new BigDecimal(reviews.get(0).get("refundAmount").toString())))>0)
                reason="Khoản giữ lại thấp hơn bồi thường đã được duyệt. Cần IT cập nhật lại phiếu hoàn cọc.";
        }
        return Map.of("canRequest",canRequest,"canSettle",reason.isEmpty(),"reason",reason,"depositAmount",deposit,
                "contractVersion",contract.get("version"),"needsItReview",hasLoans,"settled",settled);
    }
    private BigDecimal approvedDepositOffsets(UUID contractId){
        return d.jdbc.queryForObject("""
                SELECT COALESCE(SUM(i.approved_amount),0) FROM equipment_incidents i
                JOIN equipment_loans l ON l.id=i.loan_id WHERE l.contract_id=? AND i.settled_at IS NULL
                """,BigDecimal.class,contractId);
    }
    public Map<String,Object> incident(String id,Map<String,Object> p,User u){
        role(u,"ACCOUNTANT");UUID key=uuid(id);var i=d.record("incidents",id,u);
        if(i.get("contractId")==null)throw conflict("Sự cố chưa liên kết biên bản và hợp đồng để quyết toán");
        UUID ct=uuid(i.get("contractId").toString());claim(ct,u);d.lock("equipment_incidents",key,p);
        if(List.of("COMPLETED","CANCELLED").contains(d.record("contracts",ct.toString(),u).get("status")))throw conflict("Hồ sơ đào tạo đã đóng, không thay đổi khoản quyết toán");
        if(i.get("technicalNote")==null||i.get("evidence")==null)throw conflict("Chờ IT ghi nhận kết quả kỹ thuật và minh chứng");
        if(i.get("settledAt")!=null)throw conflict("Hồ sơ đã quyết toán");
        BigDecimal amount=money(p,"approvedAmount");if(amount.compareTo(new BigDecimal(i.get("compensationDue").toString()))>0)throw invalid("approvedAmount","Khoản duyệt không được vượt khoản IT đề xuất");
        String approval=text(p,"approvalReference",1000,true);
        // Approval evidence is supplied by the centre's existing approval process, not invented by the application.
        d.jdbc.update("UPDATE equipment_incidents SET approved_amount=?,approval_reference=?,version=version+1 WHERE id=?",amount,approval,key);
        if(amount.signum()==0)d.jdbc.update("UPDATE equipment_incidents SET settled_at=CURRENT_TIMESTAMP,status='RESOLVED' WHERE id=?",key);
        else if("TRANSFER".equals(text(p,"settlementMethod",40,true))){
            String ref=text(p,"bankReference",120,true),evidence=text(p,"paymentEvidence",1000,true);
            if(d.count("SELECT COUNT(*) FROM financial_transactions WHERE bank_reference=?",ref)>0)throw invalid("bankReference","Mã giao dịch đã tồn tại");
            d.jdbc.update("INSERT INTO financial_transactions(id,contract_id,incident_id,kind,amount,status,bank_reference,evidence,confirmed_by,confirmed_at) VALUES (?,?,?,'COMPENSATION',?,'CONFIRMED',?,?,?,CURRENT_TIMESTAMP)",UUID.randomUUID(),ct,key,amount,ref,evidence,u.id);
            d.jdbc.update("UPDATE equipment_incidents SET settled_at=CURRENT_TIMESTAMP,status='RESOLVED' WHERE id=?",key);
        }else if(!"DEPOSIT".equals(text(p,"settlementMethod",40,true)))throw invalid("settlementMethod","Chọn thu chuyển khoản hoặc khấu trừ cọc");
        d.jdbc.update("UPDATE training_contracts SET version=version+1 WHERE id=?",ct);
        return saved("AssetIncidentDetail_Accountant",key);
    }
    public Map<String,Object> settlement(String id,Map<String,Object> p,User u){
        role(u,"ACCOUNTANT");UUID ct=uuid(id);claim(ct,u);d.lock("training_contracts",ct,p);
        var contract=d.record("contracts",id,u);
        var context=refundContext(contract);
        if(!Boolean.TRUE.equals(context.get("canSettle")))throw conflict(context.get("reason").toString());
        BigDecimal deposits=(BigDecimal)context.get("depositAmount");
        var reviews=d.rows("SELECT * FROM deposit_refund_requests WHERE contract_id=?",ct);
        Map<String,Object> review=reviews.isEmpty()?null:reviews.get(0);
        var incidents=d.rows("SELECT i.* FROM equipment_incidents i JOIN equipment_loans l ON l.id=i.loan_id WHERE l.contract_id=? AND i.settled_at IS NULL FOR UPDATE",ct);
        BigDecimal previouslyApproved=BigDecimal.ZERO;
        for(var incident:incidents)if(incident.get("approvedAmount")!=null)previouslyApproved=previouslyApproved.add(new BigDecimal(incident.get("approvedAmount").toString()));
        BigDecimal refund=review==null?deposits.subtract(previouslyApproved):new BigDecimal(review.get("refundAmount").toString());
        BigDecimal deduct=deposits.subtract(refund);
        if(refund.signum()<0||previouslyApproved.compareTo(deduct)>0)
            throw conflict("Khoản giữ lại thấp hơn bồi thường đã được duyệt. Cần đối chiếu với IT trước khi quyết toán.");
        String ref=text(p,"bankReference",120,true),evidence=text(p,"evidence",1000,true);
        if(d.count("SELECT COUNT(*) FROM financial_transactions WHERE bank_reference=?",ref)>0||d.count("SELECT COUNT(*) FROM deposit_settlements WHERE bank_reference=?",ref)>0)throw invalid("bankReference","Mã giao dịch đã được sử dụng");
        if(!flag(p,"recipientVerified"))throw invalid("recipientVerified","Xác minh người nhận và tài khoản trước khi ghi nhận");
        String recipient=review==null?text(p,"recipient",160,true):review.get("recipientName").toString();
        String method=review==null?choice(p,"paymentMethod","TRANSFER","CASH"):"TRANSFER";
        String account=review==null?text(p,"accountDetails",500,true):review.get("bankName")+" · "+review.get("accountNumber");
        d.jdbc.update("INSERT INTO deposit_settlements(id,contract_id,deposit_amount,deduction_amount,refund_amount,recipient,payment_method,account_details,bank_reference,evidence,created_by) VALUES (?,?,?,?,?,?,?,?,?,?,?)",UUID.randomUUID(),ct,deposits,deduct,refund,recipient,method,account,ref,evidence,u.id);
        if(refund.signum()>0)d.jdbc.update("INSERT INTO financial_transactions(id,contract_id,kind,amount,status,bank_reference,evidence,confirmed_by,confirmed_at) VALUES (?,?,'REFUND',?,'CONFIRMED',?,?,?,CURRENT_TIMESTAMP)",UUID.randomUUID(),ct,refund,ref,evidence,u.id);
        for(var incident:incidents){
            UUID incidentId=uuid(incident.get("id").toString());
            BigDecimal approved=incident.get("approvedAmount")==null?BigDecimal.ZERO:new BigDecimal(incident.get("approvedAmount").toString());
            if(approved.signum()>0)d.jdbc.update("INSERT INTO financial_transactions(id,contract_id,incident_id,kind,amount,status,bank_reference,evidence,confirmed_by,confirmed_at) VALUES (?,?,?,'COMPENSATION',?,'CONFIRMED',?,?,?,CURRENT_TIMESTAMP)",UUID.randomUUID(),ct,incidentId,approved,"OFFSET-"+incidentId,incident.get("approvalReference"),u.id);
            d.jdbc.update("UPDATE equipment_incidents SET status='RESOLVED',settled_at=CURRENT_TIMESTAMP,version=version+1 WHERE id=?",incidentId);
        }
        BigDecimal remainder=deduct.subtract(previouslyApproved);
        if(review!=null&&remainder.signum()>0)d.jdbc.update("""
                INSERT INTO financial_transactions(id,contract_id,kind,amount,status,bank_reference,memo,evidence,confirmed_by,confirmed_at)
                VALUES (?,?,'COMPENSATION',?,'CONFIRMED',?,?,?,?,CURRENT_TIMESTAMP)
                """,UUID.randomUUID(),ct,remainder,"RETAIN-"+review.get("id"),review.get("reason"),evidence,u.id);
        if(contract.get("studentId") instanceof Number student)training.notify(student.longValue(),"Đã quyết toán tiền cọc",
                "Kế toán đã ghi nhận quyết toán tiền cọc cho hợp đồng "+contract.get("code")+". Số tiền hoàn: "+refund.stripTrailingZeros().toPlainString()+" đ.",
                "TuitionDetail_Student",id,u);
        d.jdbc.update("UPDATE training_contracts SET version=version+1 WHERE id=?",ct);return saved("DepositRefund",ct);
    }
}

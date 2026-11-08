package com.erhms.workspace;

import com.erhms.user.entity.User;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
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
        role(u,"ACCOUNTANT");UUID key=uuid(id);var transaction=d.record("payments",id,u);UUID contract=uuid(transaction.get("contractId").toString());claim(contract,u);d.lock("financial_transactions",key,p);
        if("CONFIRMED".equals(transaction.get("status")))throw conflict("Giao dịch này đã được xác nhận; không ghi nhận lần thứ hai");
        if(action.equals("mismatch")){d.jdbc.update("UPDATE financial_transactions SET status='MISMATCH',memo=?,version=version+1 WHERE id=?",text(p,"memo",1000,true),key);return saved("PaymentDetail_Mismatch",key);}
        if(action.equals("resolve")){if(!"MISMATCH".equals(transaction.get("status")))throw conflict("Giao dịch không ở trạng thái sai lệch");d.jdbc.update("UPDATE financial_transactions SET status='PENDING',memo=?,version=version+1 WHERE id=?",text(p,"memo",1000,true),key);return saved("PaymentDetail",key);}
        if(!"PENDING".equals(transaction.get("status")))throw conflict("Làm rõ sai lệch trước khi xác nhận giao dịch");
        var ct=d.record("contracts",contract.toString(),u);
        if(!List.of("SIGNED","ACTIVE").contains(ct.get("status")))throw conflict("Hợp đồng cần được ký và còn hiệu lực trước khi xác nhận tiền");
        String kind=transaction.get("kind").toString();if(!List.of("TUITION","DEPOSIT").contains(kind))throw conflict("Khoản hoàn cọc/bồi thường phải xử lý ở nghiệp vụ quyết toán");
        String reference=transaction.get("bankReference")==null?"":transaction.get("bankReference").toString();if(reference.isBlank())throw invalid("bankReference","Giao dịch chưa có mã ngân hàng để kiểm tra trùng");
        if(!flag(p,"identityVerified")||!flag(p,"amountVerified")||!flag(p,"referenceVerified"))throw invalid("verified","Xác nhận đã đối chiếu hợp đồng, số tiền và mã giao dịch");
        text(p,"changeNote",1000,true);BigDecimal due=new BigDecimal(ct.get(kind.equals("TUITION")?"tuitionDue":"depositDue").toString()),amount=new BigDecimal(transaction.get("amount").toString());
        if(amount.compareTo(due.subtract(sum(contract,kind)))>0)throw conflict("Số tiền vượt nghĩa vụ còn lại. Cần làm rõ sai lệch trước khi xác nhận.");
        if(d.count("SELECT COUNT(*) FROM financial_transactions WHERE bank_reference=? AND id<>? AND status='CONFIRMED'",reference,key)>0)throw conflict("Mã giao dịch đã được ghi nhận ở bản ghi khác");
        d.jdbc.update("UPDATE financial_transactions SET status='CONFIRMED',confirmed_by=?,confirmed_at=CURRENT_TIMESTAMP,version=version+1 WHERE id=?",u.id,key);
        if(kind.equals("TUITION")&&sum(contract,kind).compareTo(due)>=0){
            d.jdbc.update("UPDATE training_contracts SET status='ACTIVE',version=version+1 WHERE id=?",contract);training.autoAssign(contract);
        }
        training.notify(((Number)ct.get("studentId")).longValue(),"Đã xác nhận thanh toán","Khoản "+(kind.equals("TUITION")?"học phí":"cọc thiết bị")+" của hợp đồng "+ct.get("code")+" đã được kế toán xác nhận.","TuitionDetail_Student",contract.toString(),u);
        return saved("PaymentDetail",key);
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
        role(user,"ACCOUNTANT");UUID key=uuid(id);var contract=d.record("contracts",id,user);claim(key,user);d.lock("training_contracts",key,p);
        if(!"SIGNED".equals(contract.get("status"))||new BigDecimal(contract.get("tuitionDue").toString()).signum()!=0)throw conflict("Chỉ xác nhận miễn học phí cho hợp đồng đã ký có nghĩa vụ học phí bằng 0");
        text(p,"changeNote",1000,true);d.jdbc.update("UPDATE training_contracts SET status='ACTIVE',version=version+1 WHERE id=?",key);training.autoAssign(key);return saved("TuitionDetail",key);
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
        return saved("AssetIncidentDetail_Accountant",key);
    }
    public Map<String,Object> settlement(String id,Map<String,Object> p,User u){
        role(u,"ACCOUNTANT");UUID ct=uuid(id);var contract=d.record("contracts",id,u);claim(ct,u);d.lock("training_contracts",ct,p);
        if(d.count("SELECT COUNT(*) FROM deposit_settlements WHERE contract_id=?",ct)>0)throw conflict("Hợp đồng đã quyết toán cọc; không hoàn lần thứ hai");
        if(d.count("SELECT COUNT(*) FROM equipment_loans WHERE returned_at IS NULL AND (contract_id=? OR (contract_id IS NULL AND student_id=?))",ct,contract.get("studentId"))>0)throw conflict("Chờ IT xác nhận thu hồi tất cả thiết bị");
        if(d.count("SELECT COUNT(*) FROM equipment_incidents i JOIN equipment_loans l ON l.id=i.loan_id WHERE l.contract_id=? AND (i.approved_amount IS NULL OR i.approval_reference IS NULL)",ct)>0)throw conflict("Còn sự cố chưa có khoản xử lý và căn cứ phê duyệt");
        BigDecimal deposits=sum(ct,"DEPOSIT");
        if(deposits.signum()<=0)throw conflict("Hợp đồng chưa có cọc được xác nhận thu");
        if(sum(ct,"REFUND").signum()>0)throw conflict("Đã có giao dịch hoàn trước đây. Cần rà soát hồ sơ trước khi quyết toán");
        var incidents=d.rows("SELECT i.* FROM equipment_incidents i JOIN equipment_loans l ON l.id=i.loan_id WHERE l.contract_id=? AND i.settled_at IS NULL FOR UPDATE",ct);
        BigDecimal deduct=BigDecimal.ZERO;for(var incident:incidents)deduct=deduct.add(new BigDecimal(incident.get("approvedAmount").toString()));
        if(deduct.compareTo(deposits)>0)throw conflict("Bồi thường vượt cọc. Thu phần nghĩa vụ còn lại trước khi quyết toán cọc");
        BigDecimal refund=deposits.subtract(deduct);String ref=text(p,"bankReference",120,true),evidence=text(p,"evidence",1000,true);
        if(d.count("SELECT COUNT(*) FROM financial_transactions WHERE bank_reference=?",ref)>0||d.count("SELECT COUNT(*) FROM deposit_settlements WHERE bank_reference=?",ref)>0)throw invalid("bankReference","Mã giao dịch đã được sử dụng");
        if(!flag(p,"recipientVerified"))throw invalid("recipientVerified","Xác minh người nhận và tài khoản trước khi ghi nhận");
        d.jdbc.update("INSERT INTO deposit_settlements(id,contract_id,deposit_amount,deduction_amount,refund_amount,recipient,payment_method,account_details,bank_reference,evidence,created_by) VALUES (?,?,?,?,?,?,?,?,?,?,?)",UUID.randomUUID(),ct,deposits,deduct,refund,text(p,"recipient",160,true),choice(p,"paymentMethod","TRANSFER","CASH"),text(p,"accountDetails",500,true),ref,evidence,u.id);
        if(refund.signum()>0)d.jdbc.update("INSERT INTO financial_transactions(id,contract_id,kind,amount,status,bank_reference,evidence,confirmed_by,confirmed_at) VALUES (?,?,'REFUND',?,'CONFIRMED',?,?,?,CURRENT_TIMESTAMP)",UUID.randomUUID(),ct,refund,ref,evidence,u.id);
        for(var i:incidents){BigDecimal amount=new BigDecimal(i.get("approvedAmount").toString());if(amount.signum()>0)d.jdbc.update("INSERT INTO financial_transactions(id,contract_id,incident_id,kind,amount,status,bank_reference,evidence,confirmed_by,confirmed_at) VALUES (?,?,?,'COMPENSATION',?,'CONFIRMED',?,?,?,CURRENT_TIMESTAMP)",UUID.randomUUID(),ct,uuid(i.get("id").toString()),amount,"OFFSET-"+i.get("id"),i.get("approvalReference"),u.id);d.jdbc.update("UPDATE equipment_incidents SET status='RESOLVED',settled_at=CURRENT_TIMESTAMP,version=version+1 WHERE id=?",uuid(i.get("id").toString()));}
        d.jdbc.update("UPDATE training_contracts SET version=version+1 WHERE id=?",ct);return saved("DepositRefund",ct);
    }
}

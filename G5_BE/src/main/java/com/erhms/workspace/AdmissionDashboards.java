package com.erhms.workspace;

import com.erhms.user.entity.User;
import java.util.*;
import org.springframework.stereotype.Service;
import static com.erhms.workspace.WorkflowData.*;

@Service
public class AdmissionDashboards {
    private final WorkflowData data;
    public AdmissionDashboards(WorkflowData data){this.data=data;}

    private String source(User actor){
        // Only publish financial totals after handover, matching the HR tuition screen.
        return """
            SELECT a.*,COALESCE(c.contract_count,0) AS contract_count,
              COALESCE(c.unsigned_count,0) AS unsigned_contract_count,
              COALESCE(c.handed_over_count,0) AS handed_over_contract_count,
              CASE WHEN COALESCE(c.handed_over_count,0)>0 THEN c.tuition_due END AS tuition_due,
              CASE WHEN COALESCE(c.handed_over_count,0)>0 THEN c.tuition_paid END AS tuition_paid,
              CASE WHEN COALESCE(c.handed_over_count,0)>0 THEN c.tuition_remaining END AS tuition_remaining,
              CASE WHEN COALESCE(c.contract_count,0)=0 THEN 'NO_CONTRACT'
                   WHEN c.unsigned_count>0 THEN 'WAIT_SIGNATURE' ELSE 'SIGNED' END AS contract_progress,
              CASE WHEN a.profile_student_id IS NOT NULL THEN 'ACCOUNT_CREATED'
                   WHEN COALESCE(c.contract_count,0)=0 THEN 'WAIT_CONTRACT' ELSE 'WAIT_ACCOUNT' END AS account_progress,
              CASE WHEN COALESCE(c.contract_count,0)=0 THEN 'WAIT_CONTRACT'
                   WHEN COALESCE(c.handed_over_count,0)=0 THEN 'WAIT_HANDOVER'
                   WHEN c.tuition_remaining=0 AND c.handed_over_count<c.contract_count THEN 'WAIT_HANDOVER'
                   WHEN c.tuition_remaining=0 THEN 'PAID'
                   WHEN c.tuition_paid>0 THEN 'PARTIALLY_PAID' ELSE 'UNPAID' END AS tuition_progress
            FROM (
            """+data.base("admissions",actor)+"""
            ) a LEFT JOIN (
              SELECT consultation_id,COUNT(*) AS contract_count,
                SUM(CASE WHEN status='DRAFT' THEN 1 ELSE 0 END) AS unsigned_count,
                SUM(CASE WHEN handed_over_at IS NOT NULL THEN 1 ELSE 0 END) AS handed_over_count,
                SUM(CASE WHEN handed_over_at IS NOT NULL THEN tuition_due ELSE 0 END) AS tuition_due,
                SUM(CASE WHEN handed_over_at IS NOT NULL THEN tuition_paid ELSE 0 END) AS tuition_paid,
                SUM(CASE WHEN handed_over_at IS NOT NULL THEN GREATEST(tuition_due-tuition_paid,0) ELSE 0 END) AS tuition_remaining
              FROM (
                SELECT contract.*,COALESCE(payment.amount,0) AS tuition_paid FROM training_contracts contract
                LEFT JOIN (
                  SELECT contract_id,SUM(amount) AS amount FROM financial_transactions
                  WHERE kind='TUITION' AND status='CONFIRMED' GROUP BY contract_id
                ) payment ON payment.contract_id=contract.id
                WHERE contract.status<>'CANCELLED'
              ) contracts GROUP BY consultation_id
            ) c ON c.consultation_id=a.id
            """;
    }

    public Map<String,Object> list(String code,User actor,String query,String status,int page,String tab,boolean canForward){
        role(actor,"HR");
        boolean enrollment=code.equals("ListAdmissionEnrollment");
        if(enrollment&&!Set.of("waiting","forwarded").contains(tab))throw invalid("tab","Trang hoàn tất nhập học không hợp lệ.");
        boolean waiting=enrollment&&tab.equals("waiting");
        List<Map<String,String>> filters=waiting?List.of():enrollment?List.of(
                filter("NO_CONTRACT","Chờ lập hợp đồng"),filter("WAIT_SIGNATURE","Chờ ký hợp đồng"),
                filter("WAIT_ACCOUNT","Chờ cấp tài khoản"),filter("ACCOUNT_CREATED","Đã cấp tài khoản"),
                filter("WAIT_HANDOVER","Chờ bàn giao hợp đồng"),filter("UNPAID","Chưa đóng học phí"),
                filter("PARTIALLY_PAID","Đã đóng một phần học phí"),filter("PAID","Đã hoàn tất học phí")):
                List.of(filter("NEW","Mới tiếp nhận"),filter("NEEDS_COUNSELING","Cần tư vấn thêm"),
                        filter("DOCUMENT_APPOINTMENT","Hẹn nộp hồ sơ"),filter("DOCUMENTS_INCOMPLETE","Hồ sơ còn thiếu"),filter("NOT_PROCEEDING","Không tiếp tục đăng ký"));
        boolean legacyStage=!enrollment&&Set.of("PROFILE_PENDING","CONTRACTED","PROFILE_CREATED","CONTACTED").contains(status);
        if(!status.isEmpty()&&!legacyStage&&filters.stream().noneMatch(filter->status.equals(filter.get("value"))))throw invalid("status","Bộ lọc không thuộc dashboard này.");
        String condition=enrollment?(waiting?"document_status='DOCUMENTS_COMPLETE' AND forwarded_at IS NULL":"forwarded_at IS NOT NULL"):
                "(document_status IS NULL OR document_status<>'DOCUMENTS_COMPLETE') AND forwarded_at IS NULL";
        var args=new ArrayList<Object>();
        String searchCondition="";
        if(!query.isBlank()){
            searchCondition=" AND LOWER(COALESCE(full_name,'')||' '||COALESCE(email,'')||' '||COALESCE(phone,'')||' '||COALESCE(program_name,'')||' '||COALESCE(enrollment_course,'')) LIKE ? ESCAPE '!'";
            args.add("%"+query.strip().toLowerCase(Locale.ROOT).replace("!","!!").replace("%","!%").replace("_","!_")+"%");
        }
        condition+=searchCondition;
        var searchArgs=new ArrayList<>(args);
        if(!status.isEmpty()){
            String field=enrollment?(Set.of("NO_CONTRACT","WAIT_SIGNATURE").contains(status)?"contract_progress":Set.of("WAIT_ACCOUNT","ACCOUNT_CREATED").contains(status)?"account_progress":"tuition_progress"):
                    status.equals("DOCUMENTS_INCOMPLETE")?"document_status":"status";
            if(enrollment&&status.equals("WAIT_ACCOUNT"))condition+=" AND profile_student_id IS NULL";
            else{condition+=" AND "+field+"=?";args.add(status);}
        }
        String selected="SELECT * FROM ("+source(actor)+") dashboard WHERE "+condition;
        long total=data.count("SELECT COUNT(*) FROM ("+selected+") selected",args.toArray());
        var pageArgs=new ArrayList<>(args);pageArgs.add(page*25);
        var rows=data.rows(selected+" ORDER BY created_date DESC,id LIMIT 25 OFFSET ?",pageArgs.toArray());
        for(var row:rows)row.put("canForward",waiting&&canForward&&AdmissionProfiles.ENROLLMENT_COURSES.contains(Objects.toString(row.get("enrollmentCourse"),"")));
        String metrics=enrollment?
                "SUM(CASE WHEN contract_progress IN ('NO_CONTRACT','WAIT_SIGNATURE') THEN 1 ELSE 0 END) AS first_count,SUM(CASE WHEN profile_student_id IS NULL THEN 1 ELSE 0 END) AS second_count,SUM(CASE WHEN tuition_progress<>'PAID' THEN 1 ELSE 0 END) AS third_count":
                "SUM(CASE WHEN status IN ('NEW','CONTACTED','NEEDS_COUNSELING') THEN 1 ELSE 0 END) AS first_count,SUM(CASE WHEN status='DOCUMENT_APPOINTMENT' THEN 1 ELSE 0 END) AS second_count,SUM(CASE WHEN document_status='DOCUMENTS_INCOMPLETE' THEN 1 ELSE 0 END) AS third_count";
        var counts=data.one("SELECT "+metrics+" FROM ("+selected+") metrics",args.toArray());
        var stats=List.of(metric(enrollment?"Hồ sơ đã chuyển học vụ":"Tổng hồ sơ tiếp nhận",total),
                metric(enrollment?"Chờ lập / ký hợp đồng":"Cần tư vấn",counts.get("firstCount")),
                metric(enrollment?"Chờ cấp tài khoản":"Hẹn nộp hồ sơ",counts.get("secondCount")),
                metric(enrollment?"Cần hướng dẫn học phí":"Cần bổ sung giấy tờ",counts.get("thirdCount")));
        var result=new LinkedHashMap<String,Object>();
        result.put("resource","admissions");result.put("phase",enrollment?"ENROLLMENT":"COUNSELING");result.put("items",rows);result.put("filters",filters);
        result.put("total",total);result.put("page",page);result.put("totalPages",(total+24)/25);result.put("canImport",!enrollment);result.put("canWrite",false);
        if(enrollment){
            var tabs=data.one("SELECT SUM(CASE WHEN document_status='DOCUMENTS_COMPLETE' AND forwarded_at IS NULL THEN 1 ELSE 0 END) AS waiting,SUM(CASE WHEN forwarded_at IS NOT NULL THEN 1 ELSE 0 END) AS forwarded,SUM(CASE WHEN forwarded_at IS NOT NULL AND contract_progress IN ('NO_CONTRACT','WAIT_SIGNATURE') THEN 1 ELSE 0 END) AS pending_contracts,SUM(CASE WHEN forwarded_at IS NOT NULL AND profile_student_id IS NULL THEN 1 ELSE 0 END) AS pending_accounts FROM ("+source(actor)+") overview WHERE 1=1"+searchCondition,searchArgs.toArray());
            result.put("tab",tab);result.put("tabs",Map.of("waiting",zero(tabs.get("waiting")),"forwarded",zero(tabs.get("forwarded"))));result.put("canForward",waiting&&canForward);
            if(waiting)stats=List.of(metric("Chờ chuyển học vụ",tabs.get("waiting")),metric("Đã chuyển học vụ",tabs.get("forwarded")),metric("Học vụ chờ lập / ký hợp đồng",tabs.get("pendingContracts")),metric("Học vụ chờ cấp tài khoản",tabs.get("pendingAccounts")));
        }
        result.put("stats",stats);return result;
    }
    private static Map<String,String> filter(String value,String label){return Map.of("value",value,"label",label);}
    private static Map<String,Object> metric(String label,Object value){return Map.of("label",label,"value",value==null?0:value);}
    private static Object zero(Object value){return value==null?0:value;}
}

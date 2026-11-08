package com.erhms.workspace;

import com.erhms.user.entity.User;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.sql.*;
import java.time.*;
import java.util.*;

/** All table names and SQL fragments are application constants; request values are bound parameters. */
@Component
public class WorkflowData {
    final JdbcTemplate jdbc;
    public WorkflowData(JdbcTemplate jdbc) {this.jdbc=jdbc;}
    public List<Map<String,Object>> rows(String sql,Object...args) {
        return jdbc.query(sql,(rs,n)->{
            Map<String,Object> row=new LinkedHashMap<>();var meta=rs.getMetaData();
            for(int i=1;i<=meta.getColumnCount();i++) {
                String label=meta.getColumnLabel(i).toLowerCase(Locale.ROOT);StringBuilder key=new StringBuilder();boolean upper=false;
                for(char ch:label.toCharArray()){if(ch=='_'){upper=true;continue;}key.append(upper?Character.toUpperCase(ch):ch);upper=false;}
                Object value=rs.getObject(i);
                if(value instanceof Timestamp stamp)value=stamp.toInstant().toString();
                else if(value instanceof OffsetDateTime date)value=date.toInstant().toString();
                else if(value instanceof java.sql.Date date)value=date.toLocalDate().toString();
                else if(value instanceof UUID uuid)value=uuid.toString();
                row.put(key.toString(),value);
            }
            for(String label:List.of("profession","category","location","groupName","room","bankName")){
                Object currentLabel=row.remove(label+"Label");if(currentLabel!=null)row.put(label,currentLabel);
            }return row;
        },args);
    }
    public Map<String,Object> one(String sql,Object...args){var found=rows(sql,args);if(found.isEmpty())throw missing();return found.get(0);}
    public long count(String sql,Object...args){return jdbc.queryForObject(sql,Long.class,args);}
    public static WorkflowException missing(){return new WorkflowException(HttpStatus.NOT_FOUND,"NOT_FOUND","Không tìm thấy dữ liệu trong phạm vi được giao",Map.of());}
    public static WorkflowException conflict(String message){return new WorkflowException(HttpStatus.CONFLICT,"CONFLICT",message,Map.of());}
    public static WorkflowException invalid(String field,String message){return new WorkflowException(HttpStatus.BAD_REQUEST,"VALIDATION",message,Map.of(field,message));}
    public static UUID uuid(String value){try{return UUID.fromString(value);}catch(Exception e){throw invalid("id","Mã bản ghi không hợp lệ");}}
    public static UUID uuid(Map<String,Object> values,String key){return uuid(text(values,key,100,true));}
    public static String text(Map<String,Object> values,String key,int max,boolean required){
        Object raw=values.get(key);if(raw!=null && !(raw instanceof String))throw invalid(key,"Thông tin phải là văn bản");
        String value=raw==null?"":((String)raw).strip();if(required&&value.isEmpty())throw invalid(key,"Vui lòng nhập thông tin bắt buộc");
        if(value.length()>max)throw invalid(key,"Thông tin vượt quá "+max+" ký tự");return value;
    }
    public static long number(Map<String,Object> values,String key,long min,long max){
        try{long result=new BigDecimal(String.valueOf(values.get(key))).longValueExact();if(result<min||result>max)throw new ArithmeticException();return result;}
        catch(Exception e){throw invalid(key,"Nhập số nguyên từ "+min+" đến "+max);}
    }
    public static BigDecimal money(Map<String,Object> values,String key){
        try{BigDecimal value=new BigDecimal(String.valueOf(values.get(key)));if(value.signum()<0||value.scale()>2||value.compareTo(new BigDecimal("9999999999999999.99"))>0)throw new ArithmeticException();return value;}
        catch(Exception e){throw invalid(key,"Nhập số tiền hợp lệ, không âm, tối đa 2 chữ số thập phân");}
    }
    public static LocalDate date(Map<String,Object> values,String key,boolean required){String raw=text(values,key,40,required);try{return raw.isEmpty()?null:LocalDate.parse(raw);}catch(Exception e){throw invalid(key,"Ngày không hợp lệ");}}
    public static OffsetDateTime instant(Map<String,Object> values,String key,boolean required){String raw=text(values,key,60,required);try{return raw.isEmpty()?null:OffsetDateTime.parse(raw);}catch(Exception e){throw invalid(key,"Thời gian cần có múi giờ hợp lệ");}}
    public static String choice(Map<String,Object> values,String key,String...allowed){String value=text(values,key,80,true);if(!Arrays.asList(allowed).contains(value))throw invalid(key,"Lựa chọn không hợp lệ");return value;}
    public static boolean flag(Map<String,Object> values,String key){Object value=values.get(key);if(!(value instanceof Boolean))throw invalid(key,"Lựa chọn không hợp lệ");return (boolean)value;}
    @SuppressWarnings("unchecked") public static List<Map<String,Object>> items(Map<String,Object> values,String key){
        Object raw=values.get(key);if(!(raw instanceof List<?> list)||list.size()>500)throw invalid(key,"Danh sách không hợp lệ (tối đa 500 mục)");
        for(Object item:list)if(!(item instanceof Map<?,?>))throw invalid(key,"Danh sách không hợp lệ");return (List<Map<String,Object>>)raw;
    }
    public void lock(String table,Object id,Map<String,Object> input){
        String pk=table.equals("student_profiles")?"user_id":table.equals("payment_instructions")?"contract_id":"id";
        var found=rows("SELECT version FROM "+table+" WHERE "+pk+"=? FOR UPDATE",id);if(found.isEmpty())throw missing();
        if(number(input,"version",0,Long.MAX_VALUE)!=((Number)found.get(0).get("version")).longValue())throw conflict("Dữ liệu vừa được cập nhật. Hãy tải lại trước khi lưu.");
    }
    public static void role(User user,String...allowed){if(!Arrays.asList(allowed).contains(user.getRoleCode()))throw new AccessDeniedException("Bạn chỉ được xem, không có quyền thực hiện thao tác này");}
    public static String courseScope(User u,String alias){return switch(u.getRoleCode()){
        case "MANAGER"->alias+".manager_id="+u.id;
        case "LECTURER"->"EXISTS(SELECT 1 FROM training_classes scope_cl WHERE scope_cl.course_id="+alias+".id AND scope_cl.lecturer_id="+u.id+")";
        case "HR"->alias+".status IN ('OPEN','ACTIVE')";
        default->"1=0";};}
    public static String classScope(User u,String alias){return switch(u.getRoleCode()){
        case "MANAGER"->"("+alias+".manager_id="+u.id+" OR EXISTS(SELECT 1 FROM training_courses scope_co WHERE scope_co.id="+alias+".course_id AND scope_co.manager_id="+u.id+"))";
        case "LECTURER"->alias+".lecturer_id="+u.id;
        case "STUDENT"->"EXISTS(SELECT 1 FROM training_enrollments scope_e WHERE scope_e.class_id="+alias+".id AND scope_e.student_id="+u.id+")";
        default->"1=0";};}
    public static String contractScope(User u,String alias){return switch(u.getRoleCode()){
        case "HR"->"("+alias+".created_by="+u.id+" OR ("+alias+".created_by IS NULL AND EXISTS(SELECT 1 FROM consultation_followups scope_f WHERE scope_f.consultation_id="+alias+".consultation_id AND scope_f.assigned_to="+u.id+")))";
        case "ACCOUNTANT"->"("+alias+".accountant_id="+u.id+" OR "+alias+".accountant_id IS NULL) AND "+alias+".handed_over_at IS NOT NULL";
        case "MANAGER"->alias+".manager_id="+u.id+" AND "+alias+".handed_over_at IS NOT NULL";
        case "STUDENT"->alias+".student_id="+u.id;
        default->"1=0";};}
    public String base(String resource,User u){
        long uid=u.id;
        return switch(resource){
            case "admissions"->"SELECT r.*,COALESCE(f.status,'NEW') AS status,f.assigned_to,COALESCE(f.version,0) AS version,f.channel,f.notes,f.appointment_at FROM consultation_registrations r LEFT JOIN consultation_followups f ON f.consultation_id=r.id WHERE (f.assigned_to IS NULL OR f.assigned_to="+uid+")";
            case "students"->"SELECT u.user_id AS id,u.full_name AS name,u.full_name,u.email,u.phone,p.date_of_birth,p.identity_number,p.address,p.emergency_name,p.emergency_phone,COALESCE(p.version,0) AS version,COALESCE((SELECT en.status FROM training_enrollments en JOIN training_classes sc ON sc.id=en.class_id WHERE en.student_id=u.user_id AND "+classScope(u,"sc")+" ORDER BY en.created_at DESC FETCH FIRST 1 ROW ONLY),'PENDING') AS status FROM m_user u LEFT JOIN student_profiles p ON p.user_id=u.user_id WHERE u.role_id=3 AND "+(u.getRoleCode().equals("STUDENT")?"u.user_id="+uid:"(p.manager_id="+uid+" OR EXISTS(SELECT 1 FROM training_contracts scope_c WHERE scope_c.student_id=u.user_id AND scope_c.manager_id="+uid+" AND scope_c.handed_over_at IS NOT NULL) OR EXISTS(SELECT 1 FROM training_enrollments scope_e JOIN training_classes scope_cl ON scope_cl.id=scope_e.class_id WHERE scope_e.student_id=u.user_id AND "+classScope(u,"scope_cl")+"))");
            case "courses"->"SELECT c.*,(SELECT p.name FROM m_profession p WHERE p.id=c.profession_id) AS profession_label FROM training_courses c WHERE "+courseScope(u,"c");
            case "classes"->"SELECT cl.*,co.name AS course_name,co.code AS course_code,lect.full_name AS lecturer_name,(SELECT COUNT(*) FROM training_enrollments e WHERE e.class_id=cl.id AND e.status IN ('ACTIVE','PENDING')) AS student_count FROM training_classes cl LEFT JOIN training_courses co ON co.id=cl.course_id LEFT JOIN m_user lect ON lect.user_id=cl.lecturer_id WHERE "+classScope(u,"cl");
            case "assignments","closures"->"SELECT e.*,u.full_name AS student_name,cl.code AS class_code,cl.name AS class_name,co.name AS course_name,ct.code AS contract_code,"+(resource.equals("assignments")?"e.assignment_status":"COALESCE(e.closure_outcome,e.status)")+" AS state FROM training_enrollments e JOIN training_classes cl ON cl.id=e.class_id JOIN m_user u ON u.user_id=e.student_id LEFT JOIN training_courses co ON co.id=cl.course_id LEFT JOIN training_contracts ct ON ct.id=e.contract_id WHERE "+(List.of("ACCOUNTANT","IT_SUPPORT").contains(u.getRoleCode())?"ct.handed_over_at IS NOT NULL"+(u.getRoleCode().equals("ACCOUNTANT")?" AND "+contractScope(u,"ct"):""):classScope(u,"cl"));
            case "sessions"->"SELECT s.*,(SELECT m.name FROM m_classroom m WHERE m.id=s.classroom_id) AS room_label,cl.code AS class_code,cl.name AS class_name,cl.lecturer_id,l.full_name AS lecturer_name FROM training_sessions s JOIN training_classes cl ON cl.id=s.class_id LEFT JOIN m_user l ON l.user_id=cl.lecturer_id WHERE "+classScope(u,"cl");
            case "grades"->"SELECT g.*,e.student_id,e.class_id,u.full_name AS student_name,cl.code AS class_code FROM training_grades g JOIN training_enrollments e ON e.id=g.enrollment_id JOIN training_classes cl ON cl.id=e.class_id JOIN m_user u ON u.user_id=e.student_id WHERE "+(u.getRoleCode().equals("STUDENT")?"e.student_id="+uid+" AND g.published_at IS NOT NULL":classScope(u,"cl"));
            case "student-attendance"->"SELECT a.*,s.title,s.starts_at,cl.code AS class_code FROM training_attendance a JOIN training_sessions s ON s.id=a.session_id JOIN training_classes cl ON cl.id=s.class_id WHERE a.student_id="+uid;
            case "contracts","tuition","deposits"->"SELECT c.*,u.full_name AS student_name,co.name AS course_name,co.duration_months,"+
                    "COALESCE((SELECT SUM(t.amount) FROM financial_transactions t WHERE t.contract_id=c.id AND t.kind='TUITION' AND t.status='CONFIRMED'),0) AS tuition_paid,"+
                    "COALESCE((SELECT SUM(t.amount) FROM financial_transactions t WHERE t.contract_id=c.id AND t.kind='DEPOSIT' AND t.status='CONFIRMED'),0) AS deposit_paid,"+
                    "COALESCE((SELECT SUM(t.amount) FROM financial_transactions t WHERE t.contract_id=c.id AND t.kind='REFUND' AND t.status='CONFIRMED'),0) AS refund_paid "+
                    "FROM training_contracts c JOIN m_user u ON u.user_id=c.student_id LEFT JOIN training_courses co ON co.id=c.course_id WHERE "+contractScope(u,"c");
            case "payments"->"SELECT t.*,(SELECT m.name FROM m_bank m WHERE m.id=t.bank_id) AS bank_name_label,c.code AS contract_code,c.student_id,c.tuition_due,c.deposit_due,c.status AS contract_status,u.full_name AS student_name FROM financial_transactions t JOIN training_contracts c ON c.id=t.contract_id JOIN m_user u ON u.user_id=c.student_id WHERE "+contractScope(u,"c");
            case "requests"->"SELECT r.*,(SELECT m.name FROM m_equipment_group m WHERE m.id=r.equipment_group_id) AS group_name_label,cl.code AS class_code,u.full_name AS lecturer_name,s.full_name AS student_name FROM equipment_requests r LEFT JOIN training_classes cl ON cl.id=r.class_id JOIN m_user u ON u.user_id=r.requested_by LEFT JOIN m_user s ON s.user_id=r.student_id WHERE "+(u.getRoleCode().equals("LECTURER")?"r.requested_by="+uid+" AND "+classScope(u,"cl"):"1=1");
            case "assets"->"SELECT a.*,(SELECT m.name FROM m_equipment_group m WHERE m.id=a.equipment_group_id) AS category_label,(SELECT m.name FROM m_storage_location m WHERE m.id=a.storage_location_id) AS location_label FROM equipment_assets a WHERE 1=1";
            case "loans"->"SELECT l.*,a.code AS asset_code,a.name AS asset_name,u.full_name AS student_name,cl.code AS class_code,c.code AS contract_code FROM equipment_loans l JOIN equipment_assets a ON a.id=l.asset_id JOIN m_user u ON u.user_id=l.student_id LEFT JOIN training_classes cl ON cl.id=l.class_id LEFT JOIN training_contracts c ON c.id=l.contract_id WHERE "+switch(u.getRoleCode()){
                case "STUDENT"->"l.student_id="+uid;case "MANAGER"->"("+classScope(u,"cl")+" OR c.manager_id="+uid+")";case "ACCOUNTANT"->contractScope(u,"c");default->"1=1";};
            case "incidents"->"SELECT i.*,a.code AS asset_code,a.name AS asset_name,l.student_id,l.contract_id,u.full_name AS student_name,c.code AS contract_code FROM equipment_incidents i JOIN equipment_assets a ON a.id=i.asset_id LEFT JOIN equipment_loans l ON l.id=i.loan_id LEFT JOIN m_user u ON u.user_id=l.student_id LEFT JOIN training_contracts c ON c.id=l.contract_id WHERE "+(u.getRoleCode().equals("ACCOUNTANT")?contractScope(u,"c"):"1=1");
            case "notifications"->"SELECT n.*,CASE WHEN n.read_at IS NULL THEN 'UNREAD' ELSE 'READ' END AS status,s.full_name AS sender_name FROM user_notifications n LEFT JOIN m_user s ON s.user_id=n.sender_id WHERE n.recipient_id="+uid+" AND n.scheduled_at<=CURRENT_TIMESTAMP";
            default->throw missing();
        };
    }
    public Map<String,Object> record(String resource,String id,User u){
        Object key=resource.equals("students")?numericId(id):uuid(id);
        return one("SELECT scoped.* FROM ("+base(resource,u)+") scoped WHERE scoped.id=?",key);
    }
    public static long numericId(String id){try{long n=Long.parseLong(id);if(n<=0)throw new NumberFormatException();return n;}catch(Exception e){throw invalid("id","Mã tài khoản không hợp lệ");}}
    public List<Map<String,Object>> page(String resource,User u,String q,String status,int page){
        String sql=filtered(resource,u,q,status);List<Object> args=filters(q,status);args.add(25);args.add(page*25);
        return rows(sql+" ORDER BY id LIMIT ? OFFSET ?",args.toArray());
    }
    public long total(String resource,User u,String q,String status){return count("SELECT COUNT(*) FROM ("+filtered(resource,u,q,status)+") results",filters(q,status).toArray());}
    public List<Map<String,Object>> statistics(String resource,User user,String q,String status){
        String state=switch(resource){case "closures","assignments"->"state";case "loans"->"return_status";case "grades"->"CASE WHEN published_at IS NULL THEN 'PENDING' ELSE 'CONFIRMED' END";case "sessions"->"CASE WHEN attendance_finalized=TRUE THEN 'COMPLETED' ELSE 'PLANNED' END";default->"status";};
        var groups=rows("SELECT "+state+" AS metric_state,COUNT(*) AS count FROM ("+filtered(resource,user,q,status)+") metrics GROUP BY "+state,filters(q,status).toArray());
        long total=0,pending=0,active=0,closed=0;
        for(var group:groups){long n=((Number)group.get("count")).longValue();String code=Objects.toString(group.get("metricState"),"");total+=n;
            if(Set.of("NEW","PENDING","PLANNED","PREPARING","READY","MISMATCH","RETURN_DUE","UNREAD","IN_PROGRESS","APPOINTMENT").contains(code))pending+=n;
            else if(Set.of("COMPLETED","WITHDRAWN","RETURNED","RESOLVED","RETIRED","CLOSED","REJECTED","CANCELLED").contains(code))closed+=n;else active+=n;
        }
        return List.of(Map.of("label","Tổng bản ghi","value",total),Map.of("label","Chờ xử lý","value",pending),Map.of("label","Đang hoạt động / đã xác nhận","value",active),Map.of("label","Đã kết thúc","value",closed));
    }
    private List<Object> filters(String q,String status){var args=new ArrayList<Object>();if(!q.isEmpty())args.add("%"+q.toLowerCase(Locale.ROOT).replace("!","!!").replace("%","!%").replace("_","!_")+"%");if(!status.isEmpty())args.add(status);return args;}
    private String filtered(String resource,User u,String q,String status){
        String searchable=switch(resource){case "admissions"->"COALESCE(full_name,'')||' '||COALESCE(phone,'')||' '||COALESCE(email,'')";case "students"->"COALESCE(full_name,'')||' '||COALESCE(email,'')";case "contracts","tuition","deposits"->"COALESCE(code,'')||' '||COALESCE(student_name,'')";case "assignments","closures","payments","loans","grades"->"COALESCE(student_name,'')||' '||COALESCE(CAST(id AS VARCHAR),'')";case "notifications","sessions","requests","student-attendance"->"COALESCE(title,'')";case "incidents"->"COALESCE(description,'')||' '||COALESCE(asset_code,'')";default->"COALESCE(name,'')||' '||COALESCE(code,'')";};
        String statusKey=List.of("closures","assignments").contains(resource)?"state":resource.equals("loans")?"return_status":resource.equals("student-attendance")?"status":resource.equals("grades")?null:"status";
        if(!status.isEmpty()&&statusKey==null)throw invalid("status","Bộ lọc không được hỗ trợ");
        return "SELECT scoped.* FROM ("+base(resource,u)+") scoped WHERE 1=1"+(!q.isEmpty()?" AND LOWER("+searchable+") LIKE ? ESCAPE '!'":"")+(!status.isEmpty()?" AND "+statusKey+"=?":"");
    }
    public void requireClassStudent(UUID classId,long studentId){if(count("SELECT COUNT(*) FROM training_enrollments WHERE class_id=? AND student_id=? AND status IN ('ACTIVE','PENDING')",classId,studentId)==0)throw invalid("studentId","Học viên không thuộc lớp này");}
    public void requireRoleId(long id,int role){if(count("SELECT COUNT(*) FROM m_user WHERE user_id=? AND role_id=? AND account_enabled=TRUE AND account_locked=FALSE",id,role)==0)throw invalid("userId","Tài khoản không có vai trò phù hợp hoặc đã bị khóa");}
}

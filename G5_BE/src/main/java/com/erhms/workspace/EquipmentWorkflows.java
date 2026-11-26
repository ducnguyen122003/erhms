package com.erhms.workspace;

import com.erhms.user.StudentCode;
import com.erhms.user.entity.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static com.erhms.workspace.WorkflowData.*;
import static com.erhms.workspace.TrainingWorkflows.saved;

@Service
public class EquipmentWorkflows {
    private static final int INITIAL_REQUEST_LIMIT = 50;
    private static final int INITIAL_REQUEST_QUANTITY = 1;
    private static final ZoneId EQUIPMENT_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final int CANDIDATE_PAGE_SIZE = 25;
    private static final String INITIAL_CANDIDATES = """
            FROM training_enrollments e JOIN m_user student ON student.user_id=e.student_id
            WHERE e.class_id=? AND e.status='ACTIVE' AND e.closed_at IS NULL AND e.academic_completed_at IS NULL AND e.academic_failed_at IS NULL
              AND student.role_id=3 AND student.account_enabled=TRUE AND student.account_locked=FALSE
              AND NOT EXISTS (SELECT 1 FROM equipment_requests r
                    WHERE r.class_id=e.class_id AND r.student_id=e.student_id AND r.request_type='INITIAL' AND r.status<>'REJECTED')
              AND NOT EXISTS (SELECT 1 FROM equipment_loans loan
                    WHERE loan.student_id=e.student_id AND (loan.class_id=e.class_id OR loan.contract_id=e.contract_id))
            """;

    final WorkflowData d;final TrainingWorkflows training;
    public EquipmentWorkflows(WorkflowData d,TrainingWorkflows training){this.d=d;this.training=training;}
    public Map<String,Object> asset(String id,Map<String,Object> p,User u){
        role(u,"IT_SUPPORT");
        if(id==null)return createAssets(p);
        UUID key=uuid(id);
        d.record("assets",id,u);d.lock("equipment_assets",key,p);
        String code=text(p,"code",40,true),name=text(p,"name",160,true),status=choice(p,"status","AVAILABLE","ASSIGNED","MAINTENANCE","RETIRED");
        boolean assigned=d.count("SELECT COUNT(*) FROM equipment_loans WHERE asset_id=? AND returned_at IS NULL",key)>0;
        if(assigned&&!status.equals("ASSIGNED"))throw invalid("status","Thiết bị đang bàn giao. Thu hồi trước khi thay đổi trạng thái");
        if(!assigned&&status.equals("ASSIGNED"))throw invalid("status","Trạng thái đang cấp phát được tạo bởi biên bản bàn giao");
        if(d.count("SELECT COUNT(*) FROM equipment_assets WHERE code=? AND id<>?",code,key)>0)throw invalid("code","Mã thiết bị đã tồn tại");
        Object[] fields={code,name,text(p,"serialNumber",120,false),date(p,"purchasedOn",true),money(p,"recordedValue"),
                text(p,"assetCondition",500,true),text(p,"accessories",1000,false),status,key};
        d.jdbc.update("UPDATE equipment_assets SET code=?,name=?,serial_number=?,purchased_on=?,recorded_value=?,asset_condition=?,accessories=?,status=?,version=version+1 WHERE id=?",fields);
        return saved("AssetDetail",key);
    }
    private Map<String,Object> createAssets(Map<String,Object> p){
        String code=text(p,"code",40,true),name=text(p,"name",160,true);
        int quantity=p.containsKey("quantity")?(int)number(p,"quantity",1,500):1;
        // Older clients may still create a single asset with a non-sequential code.
        List<String> codes=p.containsKey("quantity")?AssetCodes.sequence(code,quantity):List.of(code);
        LocalDate purchasedOn=date(p,"purchasedOn",true);
        BigDecimal recordedValue=money(p,"recordedValue");
        String serial=text(p,"serialNumber",120,false),condition=text(p,"assetCondition",500,false),accessories=text(p,"accessories",1000,false);
        var duplicates=d.rows("SELECT code FROM equipment_assets WHERE code IN ("+String.join(",",Collections.nCopies(codes.size(),"?"))+") ORDER BY code",codes.toArray());
        if(!duplicates.isEmpty())throw invalid("code","Mã thiết bị "+duplicates.get(0).get("code")+" đã tồn tại. Vui lòng chọn mã bắt đầu khác.");
        UUID first=null;
        for(String next:codes){
            UUID key=UUID.randomUUID();if(first==null)first=key;
            d.jdbc.update("INSERT INTO equipment_assets(code,name,serial_number,purchased_on,recorded_value,asset_condition,accessories,status,id) VALUES (?,?,?,?,?,?,?,'AVAILABLE',?)",
                    next,name,serial,purchasedOn,recordedValue,condition.isEmpty()?null:condition,accessories,key);
        }
        return Map.of("screen",quantity==1?"AssetDetail":"ListAsset","id",first.toString(),"createdCount",quantity,
                "notice","Đã thêm "+quantity+" thiết bị ("+codes.get(0)+(quantity>1?" – "+codes.get(codes.size()-1):"")+").");
    }
    @Transactional(readOnly=true)
    public Map<String,Object> initialCandidates(String classId, String q, int page, User actor) {
        role(actor, "LECTURER");
        if (q.length()>200 || page<0 || page>100000) throw invalid("filter", "Bộ lọc không hợp lệ");
        var classes = d.rows("SELECT id,code,name FROM training_classes WHERE lecturer_id=? AND status IN ('PLANNED','ACTIVE') ORDER BY starts_on DESC NULLS LAST,code,id", actor.id);
        List<Map<String,Object>> students = List.of();
        long total = 0;
        if (!classId.isBlank()) {
            UUID key = uuid(classId);
            requireInitialClass(key, actor, false);
            String code = StudentCode.sql("student.user_id");
            String query = INITIAL_CANDIDATES + " AND LOWER(student.full_name||' '||"+code+") LIKE ? ESCAPE '!'";
            String search = "%" + q.strip().toLowerCase(Locale.ROOT).replace("!","!!").replace("%","!%").replace("_","!_") + "%";
            total = d.count("SELECT COUNT(*) " + query, key, search);
            students = d.rows("SELECT student.user_id AS id,"+code+" AS student_code,student.full_name AS name,e.created_at AS enrolled_at "
                    + query + " ORDER BY student.full_name,student.user_id LIMIT ? OFFSET ?", key, search, CANDIDATE_PAGE_SIZE, page*CANDIDATE_PAGE_SIZE);
        }
        return Map.of("classes", classes, "items", students, "total", total, "page", page,
                "totalPages", (total+CANDIDATE_PAGE_SIZE-1)/CANDIDATE_PAGE_SIZE, "size", CANDIDATE_PAGE_SIZE);
    }

    @Transactional
    public Map<String,Object> initialRequests(Map<String,Object> input, User actor) {
        role(actor, "LECTURER");
        UUID classId = uuid(input, "classId");
        var info = requestInfo(input, "INITIAL");
        if (!(input.get("studentIds") instanceof List<?> ids) || ids.isEmpty() || ids.size()>INITIAL_REQUEST_LIMIT)
            throw invalid("studentIds", "Chọn từ 1 đến " + INITIAL_REQUEST_LIMIT + " học viên");
        Set<Long> students = new LinkedHashSet<>();
        for (Object id : ids) {
            if (id==null || !students.add(number(Collections.singletonMap("studentIds", id), "studentIds", 1, Long.MAX_VALUE)))
                throw invalid("studentIds", "Danh sách học viên có mã không hợp lệ hoặc bị trùng");
        }
        requireInitialClass(classId, actor, true);
        requireInitialCandidates(classId, students);
        for (long student : students) insertRequest(classId, student, "INITIAL", info, actor);
        return Map.of("classId", classId.toString(), "createdCount", students.size(), "notice",
                "Đã gửi yêu cầu cấp thiết bị ban đầu cho " + students.size() + " học viên.");
    }

    @Transactional
    public Map<String,Object> request(Map<String,Object> input, User actor) {
        role(actor, "LECTURER");
        UUID classId = uuid(input, "classId");
        long student = number(input, "studentId", 1, Long.MAX_VALUE);
        String type = choice(input, "requestType", "INITIAL", "REPLACEMENT", "SUPPLEMENT");
        if (type.equals("INITIAL")) {
            requireInitialClass(classId, actor, true);
            requireInitialCandidates(classId, Set.of(student));
        } else {
            d.record("classes", classId.toString(), actor);
            d.requireClassStudent(classId, student);
            if(d.count("SELECT COUNT(*) FROM training_enrollments WHERE class_id=? AND student_id=? AND academic_completed_at IS NULL AND academic_failed_at IS NULL",classId,student)==0)
                throw conflict("Học viên đã được xác nhận kết quả khóa học");
        }
        return saved("EquipmentRequestDetail_Lecturer", insertRequest(classId, student, type, requestInfo(input, type), actor));
    }

    private void requireInitialClass(UUID classId, User actor, boolean lock) {
        var classroom = d.one("SELECT status FROM training_classes WHERE id=? AND lecturer_id=?" + (lock ? " FOR UPDATE" : ""), classId, actor.id);
        if (!List.of("PLANNED", "ACTIVE").contains(classroom.get("status")))
            throw conflict("Chỉ lập yêu cầu cấp ban đầu cho lớp đang chuẩn bị hoặc đang học");
    }

    private void requireInitialCandidates(UUID classId, Set<Long> students) {
        List<Object> arguments = new ArrayList<>();
        arguments.add(classId);
        arguments.addAll(students);
        String placeholders = String.join(",", Collections.nCopies(students.size(), "?"));
        Set<Long> eligible = new HashSet<>(d.jdbc.queryForList("SELECT e.student_id " + INITIAL_CANDIDATES
                + " AND e.student_id IN (" + placeholders + ")", Long.class, arguments.toArray()));
        for (long student : students) {
            if (!eligible.contains(student))
                throw conflict("Học viên " + StudentCode.fromId(student) + " đã có yêu cầu, từng nhận thiết bị hoặc không còn đủ điều kiện. Hãy tải lại danh sách.");
        }
    }

    private record RequestInfo(String title, long quantity, LocalDate neededOn, String description) {}

    private RequestInfo requestInfo(Map<String,Object> input, String type) {
        boolean initial = type.equals("INITIAL");
        long quantity = initial ? INITIAL_REQUEST_QUANTITY : number(input,"quantity",1,100);
        LocalDate neededOn = date(input,"neededOn",true);
        if (initial && !neededOn.isAfter(LocalDate.now(EQUIPMENT_ZONE)))
            throw invalid("neededOn", "Chọn ngày cần thiết bị từ ngày mai trở đi.");
        return new RequestInfo(text(input,"title",160,true), quantity, neededOn, text(input,"description",2000,true));
    }

    private UUID insertRequest(UUID classId, long student, String type, RequestInfo info, User actor) {
        UUID key = UUID.randomUUID();
        d.jdbc.update("INSERT INTO equipment_requests(id,requested_by,class_id,title,quantity,status,student_id,request_type,needed_on,description) VALUES (?,?,?,?,?,'NEW',?,?,?,?)",
                key,actor.id,classId,info.title(),info.quantity(),student,type,info.neededOn(),info.description());
        return key;
    }
    public Map<String,Object> requestState(String id,Map<String,Object> p,User u,String action){
        role(u,"IT_SUPPORT");UUID key=uuid(id);var r=d.record("requests",id,u);d.lock("equipment_requests",key,p);String before=r.get("status").toString();
        String next=switch(action){case "prepare"->"PREPARING";case "ready"->"READY";case "reject"->"REJECTED";default->throw invalid("action","Thao tác không hợp lệ");};
        if(!((before.equals("NEW")&&List.of("PREPARING","REJECTED").contains(next))||(before.equals("PREPARING")&&List.of("READY","REJECTED").contains(next))))throw conflict("Trạng thái yêu cầu không cho phép thao tác này");
        d.jdbc.update("UPDATE equipment_requests SET status=?,version=version+1 WHERE id=?",next,key);return saved("EquipmentRequestDetail_IT",key);
    }
    public Map<String,Object> handover(String id,Map<String,Object> p,User u){
        role(u,"IT_SUPPORT");UUID requestId=uuid(id);var request=d.record("requests",id,u);d.lock("equipment_requests",requestId,p);
        if(!List.of("READY","PREPARING").contains(request.get("status")))throw conflict("Yêu cầu cần được chuẩn bị trước khi bàn giao");
        if(request.get("studentId")==null||request.get("classId")==null)throw conflict("Yêu cầu chưa có học viên và lớp nhận thiết bị");
        long student=((Number)request.get("studentId")).longValue();UUID classId=uuid(request.get("classId").toString());d.requireClassStudent(classId,student);
        var contracts=d.rows("SELECT c.* FROM training_contracts c JOIN training_enrollments e ON e.contract_id=c.id WHERE e.class_id=? AND e.student_id=? AND e.status='ACTIVE' AND c.status='ACTIVE' ORDER BY c.created_at DESC",classId,student);
        if(contracts.isEmpty())throw conflict("Chưa có hợp đồng đang học gắn với lớp và học viên");var contract=contracts.get(0);UUID contractId=uuid(contract.get("id").toString());
        contract=d.one("SELECT * FROM training_contracts WHERE id=? FOR UPDATE",contractId);
        if(!"ACTIVE".equals(contract.get("status")))throw conflict("Hợp đồng không còn ở trạng thái đang đào tạo");
        if(d.count("SELECT COUNT(*) FROM training_enrollments WHERE contract_id=? AND (academic_completed_at IS NOT NULL OR academic_failed_at IS NOT NULL)",contractId)>0
                ||d.count("SELECT COUNT(*) FROM deposit_refund_requests WHERE contract_id=?",contractId)>0
                ||d.count("SELECT COUNT(*) FROM deposit_settlements WHERE contract_id=?",contractId)>0)
            throw conflict("Hồ sơ đã hoàn thành học tập hoặc đang quyết toán cọc, không bàn giao thêm thiết bị");
        // Coordinate with initial requests so the candidate check cannot race a handover.
        d.one("SELECT id FROM training_classes WHERE id=? FOR UPDATE",classId);
        d.requireClassStudent(classId,student);
        BigDecimal paid=d.jdbc.queryForObject("SELECT COALESCE(SUM(amount),0) FROM financial_transactions WHERE contract_id=? AND kind='DEPOSIT' AND status='CONFIRMED'",BigDecimal.class,contractId);
        if(paid.compareTo(new BigDecimal(contract.get("depositDue").toString()))<0)throw conflict("Chưa được kế toán xác nhận đủ điều kiện cọc theo hợp đồng");
        if(!flag(p,"equipmentChecked")||!flag(p,"studentChecked"))throw invalid("equipmentChecked","Xác nhận kiểm tra thiết bị và học viên đã kiểm tra trực tiếp");
        String evidence=text(p,"receiptEvidence",1000,true),signedBy=text(p,"signedBy",120,true);
        String studentName=d.jdbc.queryForObject("SELECT full_name FROM m_user WHERE user_id=?",String.class,student);
        if(!signedBy.equalsIgnoreCase(studentName))throw invalid("signedBy","Tên người ký nhận phải khớp học viên nhận thiết bị");
        OffsetDateTime received=instant(p,"receivedAt",true),due=instant(p,"returnDueAt",true);if(due.isBefore(received))throw invalid("returnDueAt","Hạn trả phải sau ngày bàn giao");
        var selected=items(p,"assets");if(selected.isEmpty())throw invalid("assets","Chọn thiết bị bàn giao");
        long delivered=d.count("SELECT COUNT(*) FROM equipment_loans WHERE request_id=?",requestId),quantity=((Number)request.get("quantity")).longValue();
        if(delivered+selected.size()>quantity)throw invalid("assets","Số thiết bị vượt số lượng yêu cầu còn lại");
        SortedSet<UUID> assetIds=new TreeSet<>();for(var a:selected)if(!assetIds.add(uuid(a,"id")))throw invalid("assets","Thiết bị bị trùng");
        UUID first=null;
        for(UUID asset:assetIds){var found=d.one("SELECT * FROM equipment_assets WHERE id=? FOR UPDATE",asset);
            if(!"AVAILABLE".equals(found.get("status"))||d.count("SELECT COUNT(*) FROM equipment_loans WHERE asset_id=? AND returned_at IS NULL",asset)>0)throw conflict("Thiết bị không còn sẵn sàng hoặc đã được bàn giao");
            UUID key=UUID.randomUUID();if(first==null)first=key;
            d.jdbc.update("INSERT INTO equipment_loans(id,asset_id,student_id,class_id,received_at,return_due_at,deposit_amount,return_status,request_id,contract_id,handed_over_by,receipt_evidence) VALUES (?,?,?,?,?,?,0,'ACTIVE',?,?,?,?)",key,asset,student,classId,received,due,requestId,contractId,u.id,evidence);
            d.jdbc.update("UPDATE equipment_assets SET status='ASSIGNED',version=version+1 WHERE id=?",asset);
        }
        d.jdbc.update("UPDATE equipment_requests SET status=?,version=version+1 WHERE id=?",delivered+selected.size()==quantity?"DELIVERED":"READY",requestId);
        training.notify(student,"Biên bản bàn giao thiết bị","Thiết bị đã được bàn giao và lưu minh chứng ký nhận.","StudentAssetReceipt",first.toString(),u);
        return saved("AssetHandover_Receipt",first);
    }
    public Map<String,Object> maintenance(String id,Map<String,Object> p,User u){
        role(u,"IT_SUPPORT");UUID asset=uuid(p,"assetId");d.record("assets",asset.toString(),u);d.one("SELECT id FROM equipment_assets WHERE id=? FOR UPDATE",asset);
        if(d.count("SELECT COUNT(*) FROM equipment_loans WHERE asset_id=? AND returned_at IS NULL",asset)>0)throw conflict("Thu hồi thiết bị trước khi chuyển vào kho bảo trì");
        long owner=number(p,"ownerId",1,Long.MAX_VALUE);d.requireRoleId(owner,5);UUID key=UUID.randomUUID();
        d.jdbc.update("INSERT INTO asset_maintenance(id,asset_id,reported_by,owner_id,source,maintenance_type,discovered_on,description,due_on,note) VALUES (?,?,?,?,?,?,?,?,?,?)",key,asset,u.id,owner,text(p,"source",120,true),text(p,"maintenanceType",80,true),date(p,"discoveredOn",true),text(p,"description",2000,true),date(p,"dueOn",false),text(p,"note",1000,false));
        d.jdbc.update("UPDATE equipment_assets SET status='MAINTENANCE',version=version+1 WHERE id=?",asset);return saved("AssetDetail",asset);
    }
    public Map<String,Object> completeMaintenance(String id,Map<String,Object> p,User u){
        role(u,"IT_SUPPORT");UUID asset=uuid(id);d.record("assets",id,u);d.lock("equipment_assets",asset,p);
        if(d.count("SELECT COUNT(*) FROM asset_maintenance WHERE asset_id=? AND status='OPEN'",asset)==0)throw conflict("Không có phiếu bảo trì đang mở");
        if(d.count("SELECT COUNT(*) FROM equipment_loans WHERE asset_id=? AND returned_at IS NULL",asset)>0)throw conflict("Thiết bị vẫn đang cấp phát");
        String condition=text(p,"assetCondition",500,true);d.jdbc.update("UPDATE asset_maintenance SET status='COMPLETED',version=version+1 WHERE asset_id=? AND status='OPEN'",asset);d.jdbc.update("UPDATE equipment_assets SET status='AVAILABLE',asset_condition=?,version=version+1 WHERE id=?",condition,asset);return saved("AssetDetail",asset);
    }
    public Map<String,Object> recover(String id,Map<String,Object> p,User u){
        role(u,"IT_SUPPORT");UUID key=uuid(id);var loan=d.record("loans",id,u);
        if(loan.get("contractId")!=null)d.one("SELECT id FROM training_contracts WHERE id=? FOR UPDATE",uuid(loan.get("contractId").toString()));
        else if(loan.get("classId")!=null)d.rows("""
                SELECT id FROM training_contracts WHERE id IN (SELECT contract_id FROM training_enrollments
                    WHERE class_id=? AND student_id=?) ORDER BY id FOR UPDATE
                """,uuid(loan.get("classId").toString()),loan.get("studentId"));
        d.lock("equipment_loans",key,p);
        loan=d.record("loans",id,u);
        if(loan.get("returnedAt")!=null)throw conflict("Biên bản đã được xác nhận thu hồi");
        if(!flag(p,"identityChecked")||!flag(p,"receivedChecked"))throw invalid("receivedChecked","Chỉ xác nhận khi đã thực nhận đúng thiết bị");
        OffsetDateTime when=instant(p,"returnedAt",true);if(loan.get("receivedAt")!=null&&when.toInstant().isBefore(Instant.parse(loan.get("receivedAt").toString())))throw invalid("returnedAt","Thời điểm trả trước ngày bàn giao");
        String condition=text(p,"returnCondition",1000,true),evidence=text(p,"returnEvidence",1000,true);boolean incident=flag(p,"hasIncident");UUID asset=uuid(loan.get("assetId").toString());d.one("SELECT id FROM equipment_assets WHERE id=? FOR UPDATE",asset);
        d.jdbc.update("UPDATE equipment_loans SET returned_at=?,return_status='RETURNED',return_condition=?,return_evidence=?,version=version+1 WHERE id=?",when,condition,evidence,key);
        d.jdbc.update("UPDATE equipment_assets SET status=?,asset_condition=?,version=version+1 WHERE id=?",incident?"MAINTENANCE":"AVAILABLE",condition.length()>500?condition.substring(0,500):condition,asset);
        if(incident){UUID incidentId=UUID.randomUUID();d.jdbc.update("INSERT INTO equipment_incidents(id,asset_id,reported_by,description,status,loan_id,evidence) VALUES (?,?,?,?,'OPEN',?,?)",incidentId,asset,u.id,condition,key,evidence);return saved("AssetIncidentDetail",incidentId);}
        return saved("ListAssetDetail",key);
    }
    public Map<String,Object> incident(String id,Map<String,Object> p,User u){
        role(u,"IT_SUPPORT");UUID key=id==null?UUID.randomUUID():uuid(id),asset;
        Map<String,Object> existing=id==null?null:d.record("incidents",id,u);
        asset=existing==null?uuid(p,"assetId"):uuid(existing.get("assetId").toString());d.record("assets",asset.toString(),u);
        UUID loan=existing!=null&&existing.get("loanId")!=null?uuid(existing.get("loanId").toString()):null;
        if(loan==null&&p.get("loanId")!=null&&!p.get("loanId").toString().isBlank())loan=uuid(p,"loanId");
        if(loan!=null){var found=d.record("loans",loan.toString(),u);if(!found.get("assetId").equals(asset.toString()))throw invalid("loanId","Biên bản không thuộc tài sản này");
            if(found.get("contractId")!=null){var contract=d.one("SELECT status FROM training_contracts WHERE id=? FOR UPDATE",uuid(found.get("contractId").toString()));if(List.of("COMPLETED","CANCELLED").contains(contract.get("status")))throw conflict("Hồ sơ đào tạo đã đóng; không thêm hoặc sửa sự cố của lượt bàn giao này");}
        }
        if(id!=null){d.lock("equipment_incidents",key,p);if(existing.get("approvalReference")!=null||existing.get("settledAt")!=null)throw conflict("Hồ sơ đã chuyển tài chính; không sửa đánh giá kỹ thuật");}
        String description=text(p,"description",1000,true),technical=text(p,"technicalNote",2000,true),evidence=text(p,"evidence",1000,true);BigDecimal due=money(p,"compensationDue");

        if(id==null)d.jdbc.update("INSERT INTO equipment_incidents(id,asset_id,reported_by,description,status,compensation_due,loan_id,technical_note,evidence) VALUES (?,?,?,?,'IN_PROGRESS',?,?,?,?)",key,asset,u.id,description,due,loan,technical,evidence);
        else d.jdbc.update("UPDATE equipment_incidents SET description=?,technical_note=?,evidence=?,compensation_due=?,loan_id=COALESCE(loan_id,?),status='IN_PROGRESS',version=version+1 WHERE id=?",description,technical,evidence,due,loan,key);
        return saved("AssetIncidentDetail",key);
    }
}

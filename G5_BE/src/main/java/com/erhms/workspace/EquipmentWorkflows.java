package com.erhms.workspace;

import com.erhms.user.entity.User;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static com.erhms.workspace.WorkflowData.*;
import static com.erhms.workspace.TrainingWorkflows.saved;

@Service
public class EquipmentWorkflows {
    final WorkflowData d;final TrainingWorkflows training;final MasterDataService masters;
    public EquipmentWorkflows(WorkflowData d,TrainingWorkflows training,MasterDataService masters){this.d=d;this.training=training;this.masters=masters;}
    public Map<String,Object> asset(String id,Map<String,Object> p,User u){
        role(u,"IT_SUPPORT");UUID key=id==null?UUID.randomUUID():uuid(id);
        if(id!=null){d.record("assets",id,u);d.lock("equipment_assets",key,p);}
        String code=text(p,"code",40,true),name=text(p,"name",160,true),status=choice(p,"status","AVAILABLE","ASSIGNED","MAINTENANCE","RETIRED");
        boolean assigned=d.count("SELECT COUNT(*) FROM equipment_loans WHERE asset_id=? AND returned_at IS NULL",key)>0;
        if(assigned&&!status.equals("ASSIGNED"))throw invalid("status","Thiết bị đang bàn giao. Thu hồi trước khi thay đổi trạng thái");
        if(!assigned&&status.equals("ASSIGNED"))throw invalid("status","Trạng thái đang cấp phát được tạo bởi biên bản bàn giao");
        if(d.count("SELECT COUNT(*) FROM equipment_assets WHERE code=? AND id<>?",code,key)>0)throw invalid("code","Mã thiết bị đã tồn tại");
        Object[] fields={code,name,masters.requireActive("equipmentGroups",p,"equipmentGroupId").get("name").toString(),text(p,"serialNumber",120,false),date(p,"purchasedOn",true),money(p,"recordedValue"),text(p,"assetCondition",500,true),masters.requireActive("storageLocations",p,"storageLocationId").get("name").toString(),text(p,"accessories",1000,false),status,uuid(p,"equipmentGroupId"),uuid(p,"storageLocationId"),key};
        if(id==null)d.jdbc.update("INSERT INTO equipment_assets(code,name,category,serial_number,purchased_on,recorded_value,asset_condition,location,accessories,status,equipment_group_id,storage_location_id,id) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)",fields);
        else d.jdbc.update("UPDATE equipment_assets SET code=?,name=?,category=?,serial_number=?,purchased_on=?,recorded_value=?,asset_condition=?,location=?,accessories=?,status=?,equipment_group_id=?,storage_location_id=?,version=version+1 WHERE id=?",fields);
        return saved("AssetDetail",key);
    }
    public Map<String,Object> request(Map<String,Object> p,User u){
        role(u,"LECTURER");UUID classId=uuid(p,"classId");d.record("classes",classId.toString(),u);long student=number(p,"studentId",1,Long.MAX_VALUE);d.requireClassStudent(classId,student);
        UUID key=UUID.randomUUID();d.jdbc.update("INSERT INTO equipment_requests(id,requested_by,class_id,title,quantity,status,student_id,request_type,group_name,needed_on,description,equipment_group_id) VALUES (?,?,?,?,?,'NEW',?,?,?,?,?,?)",key,u.id,classId,text(p,"title",160,true),number(p,"quantity",1,100),student,choice(p,"requestType","INITIAL","REPLACEMENT","SUPPLEMENT"),masters.requireActive("equipmentGroups",p,"equipmentGroupId").get("name").toString(),date(p,"neededOn",true),text(p,"description",2000,true),uuid(p,"equipmentGroupId"));
        return saved("EquipmentRequestDetail_Lecturer",key);
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
        role(u,"IT_SUPPORT");UUID key=uuid(id);var loan=d.record("loans",id,u);d.lock("equipment_loans",key,p);
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

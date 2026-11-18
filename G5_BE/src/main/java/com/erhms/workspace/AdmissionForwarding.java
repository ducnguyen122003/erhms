package com.erhms.workspace;

import com.erhms.user.entity.User;
import java.util.*;
import org.springframework.stereotype.Service;
import static com.erhms.workspace.WorkflowData.*;

@Service
public class AdmissionForwarding {
    private final WorkflowData data;
    public AdmissionForwarding(WorkflowData data){this.data=data;}

    public Map<String,Object> forward(Map<String,Object> input,User actor){
        role(actor,"HR");
        var items=items(input,"admissions");
        if(items.isEmpty()||items.size()>100)throw invalid("admissions","Chọn từ 1 đến 100 hồ sơ cần chuyển.");
        var selected=new TreeMap<UUID,Long>();
        for(var item:items){
            UUID id=uuid(item,"id");long version=number(item,"version",0,Long.MAX_VALUE);
            if(selected.putIfAbsent(id,version)!=null)throw invalid("admissions","Một hồ sơ chỉ được chọn một lần.");
        }
        // Lock and validate the complete selection before forwarding any record.
        for(var entry:selected.entrySet()){
            String id=entry.getKey().toString();data.record("admissions",id,actor);
            data.one("SELECT id FROM consultation_registrations WHERE id=? FOR UPDATE",entry.getKey());
            var record=data.record("admissions",id,actor);
            if(((Number)record.get("version")).longValue()!=entry.getValue())throw conflict("Hồ sơ vừa thay đổi. Hãy cập nhật danh sách và chọn lại trước khi chuyển.");
            if(record.get("forwardedAt")!=null)throw conflict("Hồ sơ đã được chuyển quản lý học vụ. Hãy cập nhật danh sách.");
            if(!"DOCUMENTS_COMPLETE".equals(record.get("documentStatus")))throw conflict("Chỉ chuyển hồ sơ đã hoàn thành đủ cả 5 loại giấy tờ.");
        }
        for(UUID id:selected.keySet()){
            data.jdbc.update("UPDATE consultation_followups SET forwarded_at=CURRENT_TIMESTAMP,forwarded_by=?,version=version+1,updated_at=CURRENT_TIMESTAMP WHERE consultation_id=?",actor.id,id);
            data.jdbc.update("INSERT INTO consultation_notes(id,consultation_id,actor_id,channel,outcome,content) VALUES (?,?,?,'IN_PERSON','FORWARDED','Tuyển sinh đã chuyển hồ sơ đủ giấy tờ sang quản lý học vụ để xử lý thủ tục nhập học.')",UUID.randomUUID(),id,actor.id);
        }
        return Map.of("forwardedCount",selected.size(),"notice","Đã chuyển "+selected.size()+" hồ sơ sang quản lý học vụ.");
    }
}

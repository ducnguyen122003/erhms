package com.erhms.workspace;

import com.erhms.admin.service.ScreenPermissionsService;
import com.erhms.user.entity.User;
import com.erhms.user.service.CurrentUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import static com.erhms.workspace.WorkflowData.*;

@Service
public class MasterDataService {
    record Definition(String table,String suffix,String role,int nameLength,List<String> references) {
        String codeLabel(){return switch(suffix){case "EquipmentGroup"->"Mã nhóm thiết bị";case "StorageLocation"->"Mã vị trí";case "Profession"->"Mã ngành nghề";case "Classroom"->"Mã phòng học";default->"Mã ngân hàng";};}
    }
    private static final Map<String,Definition> TYPES=Map.of(
        "equipmentGroups",new Definition("m_equipment_group","EquipmentGroup","IT_SUPPORT",120,List.of("equipment_assets:equipment_group_id","equipment_requests:equipment_group_id")),
        "storageLocations",new Definition("m_storage_location","StorageLocation","IT_SUPPORT",160,List.of("equipment_assets:storage_location_id")),
        "professions",new Definition("m_profession","Profession","ADMIN",120,List.of("training_courses:profession_id")),
        "classrooms",new Definition("m_classroom","Classroom","ADMIN",100,List.of("training_sessions:classroom_id")),
        "banks",new Definition("m_bank","Bank","ACCOUNTANT",160,List.of("financial_transactions:bank_id")));
    final WorkflowData d;final CurrentUser current;final ScreenCatalog catalog;final ScreenPermissionsService permissions;
    public MasterDataService(WorkflowData d,CurrentUser current,ScreenCatalog catalog,ScreenPermissionsService permissions){this.d=d;this.current=current;this.catalog=catalog;this.permissions=permissions;}
    private Definition definition(String type){var found=TYPES.get(type);if(found==null)throw missing();return found;}
    private Definition access(String code,String mode){
        var screen=catalog.get(code);User user=current.get();catalog.requireRole(screen,user.getRoleCode());permissions.require(screen.functionCode());
        return TYPES.values().stream().filter(t->!t.suffix().equals("Bank")&&code.equals(mode+t.suffix())).findFirst().orElseThrow(WorkflowData::missing);
    }
    /** IDs are the only accepted input; arbitrary labels cannot create master records through business forms. */
    public Map<String,Object> requireActive(String type,Map<String,Object> input,String field){
        var def=definition(type);UUID id;
        try{id=UUID.fromString(text(input,field,36,true));}catch(IllegalArgumentException e){throw invalid(field,"Lựa chọn danh mục không hợp lệ");}
        var rows=d.rows("SELECT id,name FROM "+def.table()+" WHERE id=? AND status='ACTIVE' FOR UPDATE",id);
        if(rows.isEmpty())throw invalid(field,"Danh mục không tồn tại hoặc đã ngừng sử dụng. Vui lòng chọn lại.");return rows.get(0);
    }
    public List<Map<String,Object>> choices(String type){return d.rows("SELECT id,code,name FROM "+definition(type).table()+" WHERE status='ACTIVE' ORDER BY name,code");}
    @Transactional(readOnly=true)
    public Map<String,Object> view(String code,String id,String q,String status,int page){
        String mode=code.startsWith("List")?"List":code.startsWith("Add")?"Add":"Edit";var def=access(code,mode);
        if(q.length()>200||page<0||page>100000||(!status.isBlank()&&!List.of("ACTIVE","INACTIVE").contains(status)))throw invalid("filter","Bộ lọc không hợp lệ");
        if(mode.equals("Edit")){if(id==null)throw missing();return Map.of("record",d.one("SELECT * FROM "+def.table()+" WHERE id=?",uuid(id)));}
        if(id!=null)throw missing();if(mode.equals("Add"))return Map.of();
        String search="%"+q.strip().toLowerCase(Locale.ROOT).replace("!","!!").replace("%","!%").replace("_","!_")+"%";
        String where=" WHERE LOWER(code||' '||name) LIKE ? ESCAPE '!'"+(status.isBlank()?"":" AND status=?");
        List<Object> args=new ArrayList<>(List.of(search));if(!status.isBlank())args.add(status);
        long total=d.count("SELECT COUNT(*) FROM "+def.table()+where,args.toArray());
        args.add(25);args.add(page*25);
        return Map.of("items",d.rows("SELECT * FROM "+def.table()+where+" ORDER BY name,code LIMIT ? OFFSET ?",args.toArray()),"total",total,"page",page,"totalPages",(total+24)/25);
    }
    @Transactional
    public Map<String,Object> save(String code,String id,Map<String,Object> input){
        var def=access(code,id==null?"Add":"Edit");UUID key=id==null?UUID.randomUUID():uuid(id);
        if(id!=null)d.lock(def.table(),key,input);
        String masterCode=text(input,"code",40,true).toUpperCase(Locale.ROOT),name=text(input,"name",def.nameLength(),true),nameKey=name.toLowerCase(Locale.ROOT);
        if(!masterCode.matches("[A-Z0-9][A-Z0-9_-]{0,39}"))throw invalid("code","Mã chỉ gồm chữ không dấu, số, dấu gạch ngang hoặc gạch dưới");
        String status=choice(input,"status","ACTIVE","INACTIVE"),description=text(input,"description",500,false);
        if(d.count("SELECT COUNT(*) FROM "+def.table()+" WHERE code=? AND id<>?",masterCode,key)>0)throw invalid("code",def.codeLabel()+" đã tồn tại");
        if(d.count("SELECT COUNT(*) FROM "+def.table()+" WHERE name_key=? AND id<>?",nameKey,key)>0)throw invalid("name","Tên danh mục đã tồn tại");
        if(id==null)d.jdbc.update("INSERT INTO "+def.table()+"(id,code,name,name_key,description,status) VALUES (?,?,?,?,?,?)",key,masterCode,name,nameKey,description,status);
        else d.jdbc.update("UPDATE "+def.table()+" SET code=?,name=?,name_key=?,description=?,status=?,version=version+1,updated_at=CURRENT_TIMESTAMP WHERE id=?",masterCode,name,nameKey,description,status,key);
        return Map.of("id",key.toString(),"screen","List"+def.suffix(),"notice",id==null?"Tạo danh mục thành công.":"Cập nhật danh mục thành công.");
    }
    @Transactional
    public Map<String,Object> delete(String code,String id,Map<String,Object> input){
        var def=access(code,"List");UUID key=uuid(id);d.lock(def.table(),key,input);
        for(String ref:def.references()){String[] parts=ref.split(":");if(d.count("SELECT COUNT(*) FROM "+parts[0]+" WHERE "+parts[1]+"=?",key)>0)throw conflict("Danh mục đang được sử dụng. Hãy chuyển sang ngừng sử dụng thay vì xóa.");}
        d.jdbc.update("DELETE FROM "+def.table()+" WHERE id=?",key);return Map.of("notice","Xóa danh mục thành công.");
    }
}

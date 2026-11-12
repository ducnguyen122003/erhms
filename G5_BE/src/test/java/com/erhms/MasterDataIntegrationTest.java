package com.erhms;

import com.erhms.user.entity.User;
import com.erhms.user.repository.UserRepository;
import com.erhms.role.repository.RoleRepository;
import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.*;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:master_data;MODE=PostgreSQL;DB_CLOSE_DELAY=-1","logging.level.root=WARN","debug=false"})
@AutoConfigureMockMvc @ActiveProfiles("test") @Transactional
class MasterDataIntegrationTest {
 @Autowired MockMvc mvc;@Autowired UserRepository users;@Autowired RoleRepository roles;@Autowired JdbcTemplate jdbc;@Autowired ObjectMapper json;
 Map<String,User> actors=new HashMap<>();
 @BeforeEach void setup(){for(String role:List.of("ADMIN","IT_SUPPORT","MANAGER","LECTURER","ACCOUNTANT","STUDENT","HR")){User u=new User();u.email=UUID.randomUUID()+"@master.test";u.fullName="Master "+role;u.passwordHash="test";u.role=roles.findByCode(role).orElseThrow();actors.put(role,users.saveAndFlush(u));}}
 String url(String code,String id){return "/api/master-data/screens/"+code+(id==null?"":"/"+id);}
 ResultActions getScreen(String role,String code,String id)throws Exception{return mvc.perform(get(url(code,id)).with(user(actors.get(role).email).roles(role)));}
 ResultActions write(String role,String code,String id,Map<String,Object> input)throws Exception{return mvc.perform((id==null?post(url(code,null)):put(url(code,id))).with(user(actors.get(role).email).roles(role)).with(csrf()).contentType("application/json").content(json.writeValueAsString(input)));}
 ResultActions remove(String role,String code,String id,int version)throws Exception{return mvc.perform(delete(url(code,id)).with(user(actors.get(role).email).roles(role)).with(csrf()).contentType("application/json").content(json.writeValueAsString(Map.of("version",version))));}
 JsonNode body(MvcResult result)throws Exception{return json.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));}
 Map<String,Object> input(String code,String name,String state,int version){return Map.of("code",code,"name",name,"status",state,"description","Danh mục kiểm thử","version",version);}
 String create(String role,String suffix,String code,String name)throws Exception{return body(write(role,"Add"+suffix,null,input(code,name,"ACTIVE",0)).andExpect(status().isOk()).andReturn()).path("id").asText();}
 ResultActions business(String role,String code,String id,Map<String,Object> input)throws Exception{return mvc.perform(post("/api/workspace/screens/"+code+(id==null?"":"/"+id)).with(user(actors.get(role).email).roles(role)).with(csrf()).contentType("application/json").content(json.writeValueAsString(input)));}

 @Test void eachCatalogueSupportsCrudAndOnlyTheResponsibleRoleCanManageIt()throws Exception{
  for(String[] spec:List.of(new String[]{"EquipmentGroup","IT_SUPPORT"},new String[]{"StorageLocation","IT_SUPPORT"},new String[]{"Profession","ADMIN"},new String[]{"Classroom","ADMIN"})){
   String suffix=spec[0],role=spec[1],id=create(role,suffix,"MD-01","Danh mục "+suffix);
   getScreen(role,"List"+suffix,null).andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1));
   getScreen(role,"Edit"+suffix,id).andExpect(status().isOk()).andExpect(jsonPath("$.record.name").value("Danh mục "+suffix));
   write(role,"Edit"+suffix,id,input("MD-02","Đã sửa "+suffix,"INACTIVE",0)).andExpect(status().isOk());
   write(role,"Edit"+suffix,id,input("MD-03","Ghi đè","ACTIVE",0)).andExpect(status().isConflict());
   for(String other:actors.keySet())if(!role.equals(other)){
    getScreen(other,"List"+suffix,null).andExpect(status().isForbidden());
    getScreen(other,"Add"+suffix,null).andExpect(status().isForbidden());
    getScreen(other,"Edit"+suffix,id).andExpect(status().isForbidden());
    write(other,"Add"+suffix,null,input("BAD","Sai quyền","ACTIVE",0)).andExpect(status().isForbidden());
    remove(other,"List"+suffix,id,1).andExpect(status().isForbidden());
   }
   remove(role,"List"+suffix,id,0).andExpect(status().isConflict());remove(role,"List"+suffix,id,1).andExpect(status().isOk());getScreen(role,"Edit"+suffix,id).andExpect(status().isNotFound());
  }
 }
 @Test void duplicateNamesCodesAndRevokedGrantsAreRejected()throws Exception{
  create("IT_SUPPORT","EquipmentGroup","EL","Điện công nghiệp");
  write("IT_SUPPORT","AddEquipmentGroup",null,input("el","Tên khác","ACTIVE",0)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.fields.code").exists());
  write("IT_SUPPORT","AddEquipmentGroup",null,input("NEW"," điện công nghiệp ","ACTIVE",0)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.fields.name").exists());
  mvc.perform(get(url("ListEquipmentGroup",null)).param("q","Điện").with(user(actors.get("IT_SUPPORT").email).roles("IT_SUPPORT"))).andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1));
  jdbc.update("DELETE FROM role_function WHERE role_id=5 AND function_code='AddEquipmentGroup'");
  write("IT_SUPPORT","AddEquipmentGroup",null,input("X","Mới","ACTIVE",0)).andExpect(status().isForbidden());
  mvc.perform(post(url("AddEquipmentGroup",null)).with(user(actors.get("IT_SUPPORT").email).roles("IT_SUPPORT")).contentType("application/json").content("{}")).andExpect(status().isForbidden());
 }
 @Test void assetReferencesArePersistedRenamesAreVisibleAndReferencedMastersCannotBeDeleted()throws Exception{
  String group=create("IT_SUPPORT","EquipmentGroup","EL","Điện"),location=create("IT_SUPPORT","StorageLocation","A","Kho A");
  var payload=new HashMap<String,Object>(Map.of("code","TS-01","name","Thiết bị","equipmentGroupId",group,"storageLocationId",location,"purchasedOn","2026-11-01","recordedValue",100,"assetCondition","Tốt","status","AVAILABLE"));
  String asset=body(business("IT_SUPPORT","AddAsset",null,payload).andExpect(status().isOk()).andReturn()).path("id").asText();
  assertEquals(UUID.fromString(group),jdbc.queryForObject("SELECT equipment_group_id FROM equipment_assets WHERE id=?",UUID.class,UUID.fromString(asset)));
  remove("IT_SUPPORT","ListEquipmentGroup",group,0).andExpect(status().isConflict());remove("IT_SUPPORT","ListStorageLocation",location,0).andExpect(status().isConflict());
  write("IT_SUPPORT","EditEquipmentGroup",group,input("EL","Điện và điện tử","ACTIVE",0)).andExpect(status().isOk());
  mvc.perform(get("/api/workspace/screens/AssetDetail/"+asset).with(user(actors.get("IT_SUPPORT").email).roles("IT_SUPPORT"))).andExpect(status().isOk()).andExpect(jsonPath("$.record.category").value("Điện và điện tử"));
  write("IT_SUPPORT","EditEquipmentGroup",group,input("EL","Điện và điện tử","INACTIVE",1)).andExpect(status().isOk());
  mvc.perform(get("/api/workspace/screens/AddAsset").with(user(actors.get("IT_SUPPORT").email).roles("IT_SUPPORT"))).andExpect(status().isOk()).andExpect(jsonPath("$.options.equipmentGroups.length()").value(0));
  payload.put("code","TS-02");business("IT_SUPPORT","AddAsset",null,payload).andExpect(status().isBadRequest()).andExpect(jsonPath("$.fields.equipmentGroupId").exists());
  payload.put("equipmentGroupId",UUID.randomUUID().toString());business("IT_SUPPORT","AddAsset",null,payload).andExpect(status().isBadRequest());
  payload.remove("equipmentGroupId");payload.put("category","Tự nhập");business("IT_SUPPORT","AddAsset",null,payload).andExpect(status().isBadRequest()).andExpect(jsonPath("$.fields.equipmentGroupId").exists());
 }
 @Test void professionAndClassroomChoicesComeFromTheirMastersAndBankSeedsAreAvailable()throws Exception{
  String profession=create("ADMIN","Profession","EL","Điện"),room=create("ADMIN","Classroom","TH01","Phòng thực hành 1");
  mvc.perform(get("/api/workspace/screens/AddCourse").with(user(actors.get("ADMIN").email).roles("ADMIN"))).andExpect(status().isOk()).andExpect(jsonPath("$.options.professions[0].id").value(profession));
  mvc.perform(get("/api/workspace/screens/TimeTable_EditSession").with(user(actors.get("MANAGER").email).roles("MANAGER"))).andExpect(status().isOk()).andExpect(jsonPath("$.options.classrooms[0].id").value(room));
  mvc.perform(get("/api/workspace/screens/ListPayment").with(user(actors.get("ACCOUNTANT").email).roles("ACCOUNTANT"))).andExpect(status().isOk()).andExpect(jsonPath("$.options.banks.length()").value(10));
  assertEquals(10,jdbc.queryForObject("SELECT COUNT(*) FROM m_bank",Integer.class));
  var course=Map.<String,Object>of("name","Khóa học","code","KH-01","professionId",profession,"durationMonths",3,"totalSessions",20,"classCapacity",20,"tuitionFee",1000,"objectives","Thực hành","status","DRAFT","assessments",List.of());
  var payload=new HashMap<>(course);payload.put("lecturers",List.of());payload.put("managerId",actors.get("MANAGER").id);business("ADMIN","AddCourse",null,payload).andExpect(status().isOk());
  remove("ADMIN","ListProfession",profession,0).andExpect(status().isConflict());
  assertEquals(UUID.fromString(profession),jdbc.queryForObject("SELECT profession_id FROM training_courses WHERE code='KH-01'",UUID.class));
 }
 @Test void masterScreensAppearInPermissionMatrixAndChangesAreAudited()throws Exception{
  String id=create("ADMIN","Classroom","A1","Phòng A1");write("ADMIN","EditClassroom",id,input("A1","Phòng A2","ACTIVE",0)).andExpect(status().isOk());remove("ADMIN","ListClassroom",id,1).andExpect(status().isOk());
  assertEquals(12,jdbc.queryForObject("SELECT COUNT(*) FROM m_function WHERE function_code IN ('ListEquipmentGroup','AddEquipmentGroup','EditEquipmentGroup','ListStorageLocation','AddStorageLocation','EditStorageLocation','ListProfession','AddProfession','EditProfession','ListClassroom','AddClassroom','EditClassroom')",Integer.class));
  assertEquals(3,jdbc.queryForObject("SELECT COUNT(*) FROM system_audit_log WHERE action_code IN ('MASTER_CREATE','MASTER_UPDATE','MASTER_DELETE') AND target_id=?",Integer.class,id));
 }
 @Test void accidentalProfessionGrantsCannotBypassTheAdminRole()throws Exception{
  String id=create("ADMIN","Profession","ADMIN-ONLY","Nghề kiểm thử");
  for(String role:actors.keySet())if(!role.equals("ADMIN")){
   for(String code:List.of("ListProfession","AddProfession","EditProfession"))jdbc.update("INSERT INTO role_function(role_id,function_code) VALUES (?,?)",actors.get(role).getRoleId(),code);
   getScreen(role,"ListProfession",null).andExpect(status().isForbidden());getScreen(role,"EditProfession",id).andExpect(status().isForbidden());
   write(role,"AddProfession",null,input("FORBIDDEN","Sai quyền","ACTIVE",0)).andExpect(status().isForbidden());
   write(role,"EditProfession",id,input("FORBIDDEN","Sai quyền","ACTIVE",0)).andExpect(status().isForbidden());remove(role,"ListProfession",id,0).andExpect(status().isForbidden());
  }
 }
}

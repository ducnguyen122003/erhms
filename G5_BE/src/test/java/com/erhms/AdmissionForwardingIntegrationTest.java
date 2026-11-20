package com.erhms;

import com.erhms.role.repository.RoleRepository;
import com.erhms.user.entity.User;
import com.erhms.user.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.*;
import org.springframework.transaction.annotation.Transactional;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:admission_forwarding;MODE=PostgreSQL;DB_CLOSE_DELAY=-1","logging.level.root=WARN","debug=false"})
@AutoConfigureMockMvc @ActiveProfiles("test") @Transactional
class AdmissionForwardingIntegrationTest {
    static final List<String> COURSES=List.of("Sửa chữa máy tính Laptop chuyên nghiệp","Sửa chữa máy tính PC & máy in","Sửa chữa Điện tử - Điện gia dụng","Sửa chữa Điện lạnh, Điện gia dụng & Inverter");
    static final String PATH="/api/workspace/screens/ListAdmissionEnrollment/new/actions/forward-profiles";
    static final List<String> TYPES=List.of("IDENTITY_CARD","CURRICULUM_VITAE","BIRTH_CERTIFICATE","ID_PHOTO","APPLICATION_FORM");
    @Autowired MockMvc mvc;@Autowired JdbcTemplate jdbc;@Autowired ObjectMapper json;@Autowired UserRepository users;@Autowired RoleRepository roles;
    User hr,otherHr,manager;String term;
    User actor(String role){var value=new User();value.email=UUID.randomUUID()+"@forward.test";value.fullName="Kiểm thử "+role;value.passwordHash="fixture";value.role=roles.findByCode(role).orElseThrow();return users.saveAndFlush(value);}
    @BeforeEach void setup(){hr=actor("HR");otherHr=actor("HR");manager=actor("MANAGER");term=UUID.randomUUID().toString();}
    UUID admission(User owner,int count){
        UUID id=UUID.randomUUID();jdbc.update("INSERT INTO consultation_registrations(id,full_name,phone,email,program_name,contact_time,created_date) VALUES (?,?,'0912345678',?,'Điện','any',CURRENT_TIMESTAMP)",id,term+" Học viên",id+"@gmail.com");
        jdbc.update("UPDATE consultation_registrations SET enrollment_course=? WHERE id=?",COURSES.get(0),id);
        jdbc.update("INSERT INTO consultation_followups(id,consultation_id,assigned_to,status,profile_submitted_at) VALUES (?,?,?,'PROFILE_PENDING',CURRENT_TIMESTAMP)",UUID.randomUUID(),id,owner.id);
        for(int index=0;index<count;index++){
            UUID file=UUID.randomUUID();byte[] bytes="%PDF-1.4".getBytes(StandardCharsets.US_ASCII);
            jdbc.update("INSERT INTO workflow_files(id,uploaded_by,filename,content_type,file_size,file_data) VALUES (?,?,'scan.pdf','application/pdf',?,?)",file,owner.id,bytes.length,bytes);
            jdbc.update("INSERT INTO admission_profile_documents(id,consultation_id,document_type,file_id,submitted_by) VALUES (?,?,?,?,?)",UUID.randomUUID(),id,TYPES.get(index),file,owner.id);
        }
        return id;
    }
    Map<String,Object> item(UUID id,long version){return Map.of("id",id.toString(),"version",version);}
    Map<String,Object> input(Map<String,Object>...items){return Map.of("admissions",List.of(items));}
    ResultActions forward(User actor,Map<String,Object> input)throws Exception{return mvc.perform(post(PATH).with(user(actor.email).roles(actor.getRoleCode())).with(csrf()).contentType("application/json").content(json.writeValueAsString(input)));}
    JsonNode body(MvcResult result)throws Exception{return json.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));}
    JsonNode page(String tab)throws Exception{return body(mvc.perform(get("/api/workspace/screens/ListAdmissionEnrollment").param("q",term).param("tab",tab).with(user(hr.email).roles("HR"))).andExpect(status().isOk()).andReturn());}
    JsonNode queue()throws Exception{return body(mvc.perform(get("/api/workspace/student-accounts").param("q",term).with(user(manager.email).roles("MANAGER"))).andExpect(status().isOk()).andReturn());}
    JsonNode detail(UUID id)throws Exception{return body(mvc.perform(get("/api/workspace/screens/ListAdmissionApplicationDetail/"+id).with(user(hr.email).roles("HR"))).andExpect(status().isOk()).andReturn());}
    String coursePath(UUID id){return "/api/workspace/screens/ListAdmissionApplicationDetail/"+id+"/actions/update-course";}
    ResultActions changeCourse(User actor,UUID id,Object course,long version)throws Exception{
        return mvc.perform(post(coursePath(id)).with(user(actor.email).roles(actor.getRoleCode())).with(csrf()).contentType("application/json").content(json.writeValueAsString(Map.of("enrollmentCourse",course,"version",version))));
    }
    Map<String,Object> contract(UUID admission){
        UUID course=UUID.randomUUID();jdbc.update("INSERT INTO training_courses(id,code,name,profession,duration_months,total_sessions,tuition_fee,objectives,status,manager_id,class_capacity) VALUES (?,?,?,'Điện',3,20,1000,'Training','OPEN',?,20)",course,course.toString(),COURSES.get(0),manager.id);
        return ClassPlanTestFixtures.withClass(jdbc,manager.id,Map.of("consultationId",admission.toString(),"courseId",course.toString(),"startsOn","2026-12-20","paymentDueOn","2026-12-19","tuitionDue",1000,"depositDue",0,"terms","Điều khoản"));
    }

    @Test void completeProfilesWaitForHrBeforeAcademicQueueDocumentsContractsAndAccountsBecomeAvailable()throws Exception{
        UUID id=admission(hr,5);String file=detail(id).path("related").path("documents").get(0).path("fileId").asText();
        assertEquals(1,page("waiting").path("total").asInt());assertEquals(0,page("forwarded").path("total").asInt());assertEquals(0,queue().path("total").asInt());
        mvc.perform(get("/api/workspace/files/"+file).with(user(manager.email).roles("MANAGER"))).andExpect(status().isNotFound());
        var contract=contract(id);
        mvc.perform(post("/api/workspace/screens/AddTrainingContract").with(user(manager.email).roles("MANAGER")).with(csrf()).contentType("application/json").content(json.writeValueAsString(contract))).andExpect(status().isBadRequest()).andExpect(jsonPath("$.fields.consultationId").exists());
        mvc.perform(post("/api/workspace/student-accounts/"+id).with(user(manager.email).roles("MANAGER")).with(csrf()).contentType("application/json").content(json.writeValueAsString(Map.of("expectedEmail",id+"@gmail.com","version",0)))).andExpect(status().isConflict());
        forward(hr,input(item(id,0))).andExpect(status().isOk()).andExpect(jsonPath("$.forwardedCount").value(1));
        var record=detail(id).path("record");assertFalse(record.path("forwardedAt").isNull());assertEquals(hr.id.longValue(),record.path("forwardedBy").asLong());assertEquals(1,record.path("version").asLong());
        assertEquals(0,page("waiting").path("total").asInt());assertEquals(1,page("forwarded").path("total").asInt());assertEquals(1,queue().path("total").asInt());
        assertEquals(COURSES.get(0),queue().path("items").get(0).path("enrollmentCourse").asText());
        mvc.perform(get("/api/workspace/screens/AddTrainingContract").with(user(manager.email).roles("MANAGER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.options.admissions[?(@.id == '"+id+"')].enrollmentCourse").value(org.hamcrest.Matchers.hasItem(COURSES.get(0))));
        mvc.perform(get("/api/workspace/files/"+file).with(user(manager.email).roles("MANAGER"))).andExpect(status().isOk());
        var saved=body(mvc.perform(post("/api/workspace/screens/AddTrainingContract").with(user(manager.email).roles("MANAGER")).with(csrf()).contentType("application/json").content(json.writeValueAsString(contract))).andExpect(status().isOk()).andReturn());
        mvc.perform(get("/api/workspace/screens/TrainingContractDetail/"+saved.path("id").asText()).with(user(manager.email).roles("MANAGER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.record.enrollmentCourse").value(COURSES.get(0)));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM consultation_notes WHERE consultation_id=? AND outcome='FORWARDED'",Integer.class,id));
    }

    @Test void batchForwardingIsAtomicAndRejectsIncompleteStaleDuplicateForeignAndRepeatedRecords()throws Exception{
        UUID first=admission(hr,5),second=admission(hr,5),partial=admission(hr,4),foreign=admission(otherHr,5);
        forward(hr,input(item(first,0),item(second,1))).andExpect(status().isConflict());
        forward(hr,input(item(first,0),item(partial,0))).andExpect(status().isConflict());
        forward(hr,input(item(first,0),item(foreign,0))).andExpect(status().isNotFound());
        forward(hr,input(item(first,0),item(first,0))).andExpect(status().isBadRequest());forward(hr,input()).andExpect(status().isBadRequest());
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM consultation_followups WHERE forwarded_at IS NOT NULL",Integer.class));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM consultation_notes WHERE outcome='FORWARDED'",Integer.class));
        forward(hr,input(item(first,0),item(second,0))).andExpect(status().isOk()).andExpect(jsonPath("$.forwardedCount").value(2));
        forward(hr,input(item(first,1))).andExpect(status().isConflict());assertEquals(2,queue().path("total").asInt());
        assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM consultation_notes WHERE outcome='FORWARDED'",Integer.class));
    }

    @Test void forwardingRequiresBothHrPermissionsCsrfAndTheAssignedScope()throws Exception{
        UUID id=admission(hr,5);var input=input(item(id,0));
        forward(otherHr,input).andExpect(status().isNotFound());forward(manager,input).andExpect(status().isForbidden());
        mvc.perform(post(PATH).with(user(hr.email).roles("HR")).contentType("application/json").content(json.writeValueAsString(input))).andExpect(status().isForbidden());
        jdbc.update("DELETE FROM role_function WHERE role_id=7 AND function_code='AdmissionConsultation'");forward(hr,input).andExpect(status().isForbidden());assertFalse(page("waiting").path("canForward").asBoolean());
        jdbc.update("INSERT INTO role_function(role_id,function_code) VALUES (7,'AdmissionConsultation')");
        jdbc.update("DELETE FROM role_function WHERE role_id=7 AND function_code='ListAdmissionEnrollment'");forward(hr,input).andExpect(status().isForbidden());
        assertNull(jdbc.queryForObject("SELECT forwarded_at FROM consultation_followups WHERE consultation_id=?",Object.class,id));
    }

    @Test void forwardedCasesKeepTheirTrackingPageAndAuditWhenPdfsAreLaterEdited()throws Exception{
        UUID id=admission(hr,5);forward(hr,input(item(id,0))).andExpect(status().isOk());String date=detail(id).path("record").path("forwardedAt").asText();
        var documents=new ArrayList<Map<String,Object>>();
        for(var document:detail(id).path("related").path("documents"))if(!document.path("documentType").asText().equals("ID_PHOTO"))documents.add(Map.of("type",document.path("documentType").asText(),"files",List.of(document.path("fileId").asText())));
        mvc.perform(post("/api/workspace/screens/ListAdmissionApplicationDetail/"+id+"/actions/update-profile").with(user(hr.email).roles("HR")).with(csrf()).contentType("application/json").content(json.writeValueAsString(Map.of("version",1,"documents",documents)))).andExpect(status().isOk());
        assertEquals(date,detail(id).path("record").path("forwardedAt").asText());assertEquals("DOCUMENTS_INCOMPLETE",detail(id).path("record").path("documentStatus").asText());
        assertEquals(1,page("forwarded").path("total").asInt());assertEquals(0,page("waiting").path("total").asInt());assertEquals(1,queue().path("total").asInt());
        mvc.perform(get("/api/workspace/screens/ListAdmissionApplication").param("q",term).with(user(hr.email).roles("HR"))).andExpect(status().isOk()).andExpect(jsonPath("$.total").value(0));
    }

    @Test void allFourCoursesCanBeChangedOnASubmittedProfileWithoutChangingItsOriginalInterestOrDocuments()throws Exception{
        UUID id=admission(hr,5);jdbc.update("UPDATE consultation_registrations SET enrollment_course=NULL WHERE id=?",id);
        var initial=detail(id);assertTrue(initial.path("canEditCourse").asBoolean());
        assertEquals(json.valueToTree(COURSES),initial.path("options").path("enrollmentCourses"));
        for(int index=0;index<COURSES.size();index++){
            changeCourse(hr,id,"  "+COURSES.get(index)+"  ",index).andExpect(status().isOk());
            var record=detail(id).path("record");assertEquals(COURSES.get(index),record.path("enrollmentCourse").asText());
            assertEquals(index+1,record.path("version").asLong());assertEquals("PROFILE_PENDING",record.path("status").asText());
            assertEquals("Điện",record.path("programName").asText());assertEquals("DOCUMENTS_COMPLETE",record.path("documentStatus").asText());
        }
        assertEquals(initial.path("related").path("documents"),detail(id).path("related").path("documents"));
        assertEquals(4,jdbc.queryForObject("SELECT COUNT(*) FROM consultation_notes WHERE consultation_id=? AND outcome='COURSE_UPDATED'",Integer.class,id));
        forward(hr,input(item(id,0))).andExpect(status().isConflict());
        forward(hr,input(item(id,4))).andExpect(status().isOk());
        assertEquals(COURSES.get(3),queue().path("items").get(0).path("enrollmentCourse").asText());
        assertFalse(detail(id).path("canEditCourse").asBoolean());
        changeCourse(hr,id,COURSES.get(0),5).andExpect(status().isConflict());
        changeCourse(hr,id,COURSES.get(3),5).andExpect(status().isConflict());
        assertEquals(COURSES.get(3),detail(id).path("record").path("enrollmentCourse").asText());
    }

    @Test void selectingCourseForANewRegistrationClaimsTheProfileAndPreservesItsStage()throws Exception{
        UUID id=admission(hr,0);jdbc.update("DELETE FROM consultation_followups WHERE consultation_id=?",id);
        jdbc.update("UPDATE consultation_registrations SET enrollment_course=NULL WHERE id=?",id);
        changeCourse(hr,id,COURSES.get(1),0).andExpect(status().isOk());
        var record=detail(id).path("record");assertEquals("NEW",record.path("status").asText());
        assertEquals(hr.id.longValue(),record.path("assignedTo").asLong());assertEquals(1,record.path("version").asLong());
        changeCourse(otherHr,id,COURSES.get(2),1).andExpect(status().isNotFound());
        changeCourse(hr,id,COURSES.get(1),1).andExpect(status().isOk());assertEquals(1,detail(id).path("record").path("version").asLong());
    }

    @Test void courseChangesRejectInvalidStaleForeignAndUnauthorizedRequests()throws Exception{
        UUID id=admission(hr,4);
        for(Object invalid:List.of("","Khóa học không hỗ trợ",123))changeCourse(hr,id,invalid,0).andExpect(status().isBadRequest()).andExpect(jsonPath("$.fields.enrollmentCourse").exists());
        changeCourse(hr,id,COURSES.get(1),1).andExpect(status().isConflict());
        changeCourse(otherHr,id,COURSES.get(1),0).andExpect(status().isNotFound());
        changeCourse(manager,id,COURSES.get(1),0).andExpect(status().isForbidden());
        mvc.perform(post(coursePath(id)).with(user(hr.email).roles("HR")).contentType("application/json").content(json.writeValueAsString(Map.of("enrollmentCourse",COURSES.get(1),"version",0)))).andExpect(status().isForbidden());
        jdbc.update("DELETE FROM role_function WHERE role_id=7 AND function_code='AdmissionConsultation'");
        assertFalse(detail(id).path("canEditCourse").asBoolean());changeCourse(hr,id,COURSES.get(1),0).andExpect(status().isForbidden());
        assertEquals(COURSES.get(0),detail(id).path("record").path("enrollmentCourse").asText());
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM consultation_notes WHERE consultation_id=?",Integer.class,id));
    }

    @Test void missingCoursePreventsTheWholeBatchFromBeingForwardedAndKeepsTheProfileVisibleForCorrection()throws Exception{
        UUID ready=admission(hr,5),missing=admission(hr,5);
        jdbc.update("UPDATE consultation_registrations SET enrollment_course=NULL WHERE id=?",missing);
        var page=page("waiting");assertEquals(2,page.path("total").asInt());
        for(var row:page.path("items"))assertEquals(row.path("id").asText().equals(ready.toString()),row.path("canForward").asBoolean());
        forward(hr,input(item(ready,0),item(missing,0))).andExpect(status().isBadRequest()).andExpect(jsonPath("$.fields.enrollmentCourse").exists());
        assertEquals(0,queue().path("total").asInt());assertEquals(0,page("forwarded").path("total").asInt());
        changeCourse(hr,missing,COURSES.get(2),0).andExpect(status().isOk());
        forward(hr,input(item(ready,0),item(missing,1))).andExpect(status().isOk()).andExpect(jsonPath("$.forwardedCount").value(2));
    }

    ResultActions saveContract(String code,String id,Map<String,Object> values)throws Exception{
        values=ClassPlanTestFixtures.withClass(jdbc,manager.id,values);
        return mvc.perform(post("/api/workspace/screens/"+code+(id==null?"":"/"+id)).with(user(manager.email).roles("MANAGER")).with(csrf()).contentType("application/json").content(json.writeValueAsString(values)));
    }
    JsonNode contractView(String id)throws Exception{
        return body(mvc.perform(get("/api/workspace/screens/TrainingContractDetail/"+id).with(user(manager.email).roles("MANAGER"))).andExpect(status().isOk()).andReturn()).path("record");
    }

    @Test void registeredCourseAndBaseTuitionAreResolvedForAllFourProgramsWithoutTakingAnotherManagersCourses()throws Exception{
        long[] prices={14200000,13000000,11000000,21900000};int[] months={4,4,4,7};
        var options=body(mvc.perform(get("/api/workspace/screens/AddTrainingContract").with(user(manager.email).roles("MANAGER"))).andExpect(status().isOk()).andReturn()).path("options");
        var catalog=options.path("enrollmentCourseCatalog");assertEquals(4,catalog.size());
        for(int index=0;index<COURSES.size();index++){
            assertEquals(COURSES.get(index),catalog.get(index).path("name").asText());assertEquals(prices[index],catalog.get(index).path("tuitionFee").asLong());assertEquals(months[index],catalog.get(index).path("durationMonths").asInt());
            UUID admission=admission(hr,5);jdbc.update("UPDATE consultation_registrations SET enrollment_course=? WHERE id=?",COURSES.get(index),admission);
            forward(hr,input(item(admission,0))).andExpect(status().isOk());
            var values=new LinkedHashMap<>(contract(admission));String course=values.get("courseId").toString();
            jdbc.update("UPDATE training_courses SET name=? WHERE id=?",COURSES.get(index),UUID.fromString(course));
            values.remove("courseId");values.remove("tuitionDue");
            var saved=body(saveContract("AddTrainingContract",null,values).andExpect(status().isOk()).andReturn());
            var record=contractView(saved.path("id").asText());assertEquals(course,record.path("courseId").asText());assertEquals(prices[index],record.path("tuitionDue").asLong());
            assertEquals(COURSES.get(index),record.path("enrollmentCourse").asText());
            assertEquals(COURSES.get(index),record.path("courseName").asText());assertEquals(months[index],record.path("durationMonths").asInt());
        }
    }

    @Test void negotiatedTuitionIsPreservedWhenTheDraftIsOpenedAndEdited()throws Exception{
        UUID admission=admission(hr,5);forward(hr,input(item(admission,0))).andExpect(status().isOk());
        var values=new LinkedHashMap<>(contract(admission));values.put("tuitionDue",13500000);
        String id=body(saveContract("AddTrainingContract",null,values).andExpect(status().isOk()).andReturn()).path("id").asText();
        // Historical drafts keep their agreed price and course even if the catalog name later changes.
        jdbc.update("UPDATE training_courses SET name='Legacy course name' WHERE id=?",UUID.fromString(values.get("courseId").toString()));
        mvc.perform(get("/api/workspace/screens/EditTrainingContract/"+id).with(user(manager.email).roles("MANAGER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.record.tuitionDue").value(13500000));
        values.remove("tuitionDue");values.put("version",0);values.put("terms","Điều khoản được bổ sung");
        saveContract("EditTrainingContract",id,values).andExpect(status().isOk());assertEquals(13500000,contractView(id).path("tuitionDue").asLong());
        assertEquals(COURSES.get(0),contractView(id).path("courseName").asText());
    }

    @Test void mismatchedCoursesAndCoursesOutsideTheManagerScopeCannotBeUsedForTheRegisteredProgram()throws Exception{
        UUID admission=admission(hr,5);forward(hr,input(item(admission,0))).andExpect(status().isOk());
        var values=new LinkedHashMap<>(contract(admission));UUID course=UUID.fromString(values.get("courseId").toString());
        jdbc.update("UPDATE training_courses SET name=? WHERE id=?",COURSES.get(1),course);
        saveContract("AddTrainingContract",null,values).andExpect(status().isBadRequest()).andExpect(jsonPath("$.fields.courseId").exists());
        jdbc.update("UPDATE training_courses SET name=?,manager_id=? WHERE id=?",COURSES.get(0),actor("MANAGER").id,course);
        saveContract("AddTrainingContract",null,values).andExpect(status().isNotFound());
        values.remove("courseId");values.remove("classId");String id=body(saveContract("AddTrainingContract",null,values).andExpect(status().isOk()).andReturn()).path("id").asText();
        assertFalse(contractView(id).path("courseId").isNull());assertEquals(manager.id.longValue(),contractView(id).path("managerId").asLong());
        assertEquals(COURSES.get(0),contractView(id).path("courseName").asText());
    }

    @Test void selectingAPlannedClassResolvesDuplicateCourseNamesAndDuplicateContractsAreStillRejected()throws Exception{
        UUID admission=admission(hr,5);forward(hr,input(item(admission,0))).andExpect(status().isOk());
        var values=new LinkedHashMap<>(contract(admission));contract(admission);
        values.remove("courseId");values.put("tuitionDue",0);
        String id=body(saveContract("AddTrainingContract",null,values).andExpect(status().isOk()).andReturn()).path("id").asText();
        assertEquals(0,contractView(id).path("tuitionDue").asLong());assertFalse(contractView(id).path("courseId").isNull());
        // Leave intake eligible so this specifically exercises duplicate contract detection.
        jdbc.update("UPDATE consultation_followups SET status='PROFILE_PENDING' WHERE consultation_id=?",admission);
        saveContract("AddTrainingContract",null,values).andExpect(status().isBadRequest()).andExpect(jsonPath("$.fields.consultationId").exists());
    }

    @Test void allFourAdmissionProgramsRetainTheirPricesWhenAPlannedClassSuppliesTheOperationalCourse()throws Exception{
        long[] prices={14200000,13000000,11000000,21900000};int[] months={4,4,4,7};
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM training_courses WHERE manager_id=?",Integer.class,manager.id));
        for(int index=0;index<COURSES.size();index++){
            UUID admission=admission(hr,5);jdbc.update("UPDATE consultation_registrations SET enrollment_course=? WHERE id=?",COURSES.get(index),admission);
            forward(hr,input(item(admission,0))).andExpect(status().isOk());
            var values=new LinkedHashMap<String,Object>(Map.of("consultationId",admission.toString(),"startsOn","2026-12-20","paymentDueOn","2026-12-19","depositDue",0,"terms","Điều khoản"));
            values.put("registeredCourseName","Client must not choose the snapshot");values.put("registeredDurationMonths",99);
            String id=body(saveContract("AddTrainingContract",null,values).andExpect(status().isOk()).andReturn()).path("id").asText();
            var record=contractView(id);assertFalse(record.path("courseId").isNull());assertFalse(record.path("classId").isNull());assertEquals(COURSES.get(index),record.path("courseName").asText());
            assertEquals(months[index],record.path("durationMonths").asInt());assertEquals(prices[index],record.path("tuitionDue").asLong());assertEquals(manager.id.longValue(),record.path("managerId").asLong());
            mvc.perform(get("/api/workspace/screens/TrainingContractDetail/"+id).with(user(actor("MANAGER").email).roles("MANAGER"))).andExpect(status().isNotFound());
            values.put("version",0);values.put("tuitionDue",prices[index]-1000000);
            saveContract("EditTrainingContract",id,values).andExpect(status().isOk());
            values.remove("tuitionDue");values.put("version",1);
            saveContract("EditTrainingContract",id,values).andExpect(status().isOk());assertEquals(prices[index]-1000000,contractView(id).path("tuitionDue").asLong());
        }
        assertEquals(4,jdbc.queryForObject("SELECT COUNT(*) FROM training_courses WHERE manager_id=?",Integer.class,manager.id));
    }
}

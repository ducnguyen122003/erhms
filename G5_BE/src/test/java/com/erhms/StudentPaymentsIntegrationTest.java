package com.erhms;

import com.erhms.role.repository.RoleRepository;
import com.erhms.user.entity.User;
import com.erhms.user.repository.UserRepository;
import com.erhms.workspace.EnrollmentCourses;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.*;
import org.springframework.transaction.annotation.Transactional;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:student_payments;MODE=PostgreSQL;DB_CLOSE_DELAY=-1","logging.level.root=WARN","debug=false"})
@AutoConfigureMockMvc @ActiveProfiles("test") @Transactional
class StudentPaymentsIntegrationTest {
    static final String API="/api/workspace/student-payments";
    static final byte[] PDF="%PDF-1.4\nStudent receipt".getBytes(StandardCharsets.US_ASCII);
    static final UUID BIDV=UUID.fromString("55000000-0000-0000-0000-000000000002");
    @Autowired MockMvc mvc; @Autowired JdbcTemplate jdbc; @Autowired ObjectMapper json;
    @Autowired UserRepository users; @Autowired RoleRepository roles;
    User student,otherStudent,manager,otherManager,accountant,otherAccountant,hr,admin;
    UUID course,cl,contract;
    final String paidOn=LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")).minusDays(1).toString();

    @BeforeEach void setup(){
        manager=actor("MANAGER");otherManager=actor("MANAGER");accountant=actor("ACCOUNTANT");otherAccountant=actor("ACCOUNTANT");hr=actor("HR");admin=actor("ADMIN");student=actor("STUDENT");otherStudent=actor("STUDENT");
        student.fullName="Nguyễn Ánh Đức";users.saveAndFlush(student);
        jdbc.update("INSERT INTO student_profiles(user_id,manager_id) VALUES (?,?),(?,?)",student.id,manager.id,otherStudent.id,otherManager.id);
        course=UUID.randomUUID();cl=UUID.randomUUID();
        jdbc.update("INSERT INTO training_courses(id,code,name,profession,duration_months,total_sessions,tuition_fee,objectives,status,manager_id,class_capacity) VALUES (?,?,?,'Máy tính',4,20,1000,'Thực hành','OPEN',?,20)",course,course.toString(),EnrollmentCourses.NAMES.get(0),manager.id);
        jdbc.update("INSERT INTO training_classes(id,code,name,starts_on,status,total_sessions,course_id,manager_id,capacity) VALUES (?,?,'Lớp thanh toán',DATE '2027-01-04','PLANNED',20,?,?,20)",cl,cl.toString(),course,manager.id);
        contract=contract(student,manager,accountant,1000,200);
        jdbc.update("UPDATE training_contracts SET class_id=?,course_id=? WHERE id=?",cl,course,contract);
    }
    User actor(String role){var u=new User();u.email=UUID.randomUUID()+"@payment.test";u.fullName="Kiểm thử "+role;u.passwordHash="fixture";u.role=roles.findByCode(role).orElseThrow();return users.saveAndFlush(u);}
    UUID contract(User learner,User owner,User reviewer,int tuition,int deposit){
        UUID id=UUID.randomUUID();
        jdbc.update("INSERT INTO training_contracts(id,code,student_id,student_email,applicant_name,manager_id,accountant_id,registered_course_name,registered_duration_months,tuition_due,deposit_due,status,signature_approved_at,signature_approved_by,handed_over_at,starts_on) VALUES (?,?,?,?,?,?,?,?,?,?,?,'SIGNED',CURRENT_TIMESTAMP,?,CURRENT_TIMESTAMP,DATE '2027-01-04')",id,"HD-"+id,learner.id,learner.email,learner.fullName,owner.id,reviewer.id,EnrollmentCourses.NAMES.get(0),4,tuition,deposit,owner.id);
        return id;
    }
    Map<String,Object> values(Object...pairs){var p=new LinkedHashMap<String,Object>();for(int i=0;i<pairs.length;i+=2)p.put((String)pairs[i],pairs[i+1]);return p;}
    JsonNode body(MvcResult result)throws Exception{return json.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));}
    ResultActions read(User actor,String path)throws Exception{return mvc.perform(get(path).with(user(actor.email).roles(actor.getRoleCode())));}
    ResultActions write(User actor,String path,Map<String,Object> input)throws Exception{return mvc.perform(post(path).with(user(actor.email).roles(actor.getRoleCode())).with(csrf()).contentType("application/json").content(json.writeValueAsString(input)));}
    JsonNode guide(User actor,UUID id)throws Exception{return body(read(actor,API+"/contracts/"+id).andExpect(status().isOk()).andReturn());}
    String memo(User actor,UUID id,String kind)throws Exception{for(JsonNode option:guide(actor,id).path("paymentOptions"))if(kind.equals(option.path("kind").asText()))return option.path("transferMemo").asText();throw new AssertionError("Missing payment option "+kind);}
    String receipt(User actor)throws Exception{return upload(actor,"TuitionDetail_Student",PDF,"bill.pdf").path("id").asText();}
    JsonNode upload(User actor,String screen,byte[] bytes,String filename)throws Exception{return body(mvc.perform(multipart("/api/workspace/files").file(new MockMultipartFile("file",filename,"application/pdf",bytes)).param("screen",screen).with(user(actor.email).roles(actor.getRoleCode())).with(csrf())).andExpect(status().isOk()).andReturn());}
    Map<String,Object> submission(User actor,UUID id,String kind,int amount,String file)throws Exception{return values("kind",kind,"amount",amount,"payerName",actor.fullName,"paidOn",paidOn,"transferMemo",memo(actor,id,kind),"receiptFileId",file,"declaredBankReference","CLAIM-"+UUID.randomUUID(),"note","Bill học viên cung cấp");}
    Map<String,Object> combinedSubmission(int tuition,int deposit)throws Exception{
        var p=submission(student,contract,"TUITION",tuition+deposit,receipt(student));p.put("kind","COMBINED");p.put("tuitionAmount",tuition);p.put("depositAmount",deposit);return p;
    }
    String submitCombined(int tuition,int deposit)throws Exception{return body(write(student,API+"/contracts/"+contract+"/submissions",combinedSubmission(tuition,deposit)).andExpect(status().isOk()).andReturn()).path("id").asText();}
    String submit(User actor,UUID id,String kind,int amount)throws Exception{return body(write(actor,API+"/contracts/"+id+"/submissions",submission(actor,id,kind,amount,receipt(actor))).andExpect(status().isOk()).andReturn()).path("id").asText();}
    JsonNode review(String id)throws Exception{return body(read(accountant,API+"/review/"+id).andExpect(status().isOk()).andReturn());}
    Map<String,Object> confirmation(String id,String reference)throws Exception{
        JsonNode d=review(id),s=d.path("submission");
        return values("version",s.path("version").asLong(),"bankReference",reference,"statementAmount",s.path("amount").decimalValue(),"statementMemo",d.path("expectedTransferMemo").asText(),"statementDate",paidOn,"identityVerified",true,"amountVerified",true,"referenceVerified",true,"changeNote","Đã đối chiếu sao kê ngân hàng");
    }
    JsonNode confirm(String id,String reference)throws Exception{return body(write(accountant,API+"/review/"+id+"/confirm",confirmation(id,reference)).andExpect(status().isOk()).andReturn());}
    String statusOfContract(UUID id){return jdbc.queryForObject("SELECT status FROM training_contracts WHERE id=?",String.class,id);}
    int received(UUID id){return jdbc.queryForObject("SELECT COALESCE(SUM(amount),0) FROM financial_transactions WHERE contract_id=? AND kind='TUITION' AND status='CONFIRMED'",Integer.class,id);}
    int transactions(UUID id){return jdbc.queryForObject("SELECT COUNT(*) FROM financial_transactions WHERE contract_id=?",Integer.class,id);}
    int enrollments(){return jdbc.queryForObject("SELECT COUNT(*) FROM training_enrollments WHERE student_id=?",Integer.class,student.id);}
    void assertActiveContractEnrollment(){
        assertEquals(1,enrollments());
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM training_enrollments WHERE contract_id=? AND class_id=? AND student_id=? AND status='ACTIVE'",Integer.class,contract,cl,student.id));
    }
    String imported(String kind,int amount,String reference,String transferMemo)throws Exception{
        var p=values("contractId",contract.toString(),"kind",kind,"amount",amount,"bankReference",reference,"payerName",student.fullName,"bankId",BIDV.toString(),"memo",transferMemo,"evidence","Sao kê kế toán nhập");
        return body(write(accountant,"/api/workspace/screens/ListPayment/new/actions/import",p).andExpect(status().isOk()).andReturn()).path("id").asText();
    }
    void confirmImported(String id)throws Exception{write(accountant,"/api/workspace/screens/PaymentDetail/"+id+"/actions/confirm",values("version",0,"identityVerified",true,"amountVerified",true,"referenceVerified",true,"changeNote","Đã đối chiếu sao kê")).andExpect(status().isOk());}

    @Test void submittedBillsDoNotCreditFeesAndFullAccountantConfirmationEnrollsTheStudent()throws Exception{
        JsonNode initial=guide(student,contract);assertTrue(initial.path("canSubmit").asBoolean());assertEquals(0,initial.path("contract").path("tuitionPaid").asInt());assertEquals(0,enrollments());
        assertTrue(initial.path("contract").path("enrolledClassCode").asText("").isBlank());
        String first=submit(student,contract,"TUITION",400);
        assertEquals(0,received(contract));assertEquals(0,transactions(contract));assertEquals("SIGNED",statusOfContract(contract));assertEquals(0,enrollments());
        assertTrue(body(read(accountant,API+"/review").andExpect(status().isOk()).andReturn()).toString().contains(first));
        assertEquals("PENDING",review(first).path("submission").path("status").asText());
        assertTrue(guide(student,contract).path("submissions").toString().contains(first));
        confirm(first,"BANK-PARTIAL-"+UUID.randomUUID());assertEquals(400,received(contract));assertEquals("SIGNED",statusOfContract(contract));assertEquals(0,enrollments());
        assertTrue(guide(student,contract).path("contract").path("enrolledClassCode").asText("").isBlank());
        String second=submit(student,contract,"TUITION",600);assertEquals(0,enrollments());
        confirm(second,"BANK-FULL-"+UUID.randomUUID());
        assertEquals(1000,received(contract));assertEquals("ACTIVE",statusOfContract(contract));assertActiveContractEnrollment();
        JsonNode paid=guide(student,contract).path("contract");
        assertEquals(cl.toString(),paid.path("enrolledClassCode").asText());
        assertEquals("Lớp thanh toán",paid.path("enrolledClassName").asText());
    }

    @Test void transferGuideAndQrUseTheConfiguredRecipientAndLearnerNameWithStableStudentCode()throws Exception{
        var guide=guide(student,contract);assertTrue(guide.path("bank").toString().contains("3419120803"));assertTrue(guide.path("bank").toString().contains("NGUYEN ANH DUC"));assertTrue(guide.path("bank").toString().contains("BIDV"));
        String code=String.format(Locale.ROOT,"HV%06d",student.id);
        assertEquals(code,guide.path("contract").path("studentCode").asText());
        String tuition=memo(student,contract,"TUITION"),deposit=memo(student,contract,"DEPOSIT");assertEquals("NGUYEN ANH DUC "+code,tuition);assertEquals(tuition,deposit);
        UUID second=contract(student,manager,accountant,1000,200);assertEquals(tuition,memo(student,second,"TUITION"));
        var qr=body(read(student,API+"/contracts/"+contract+"/qr?kind=TUITION&amount=1000").andExpect(status().isOk()).andReturn());
        assertEquals(tuition,qr.path("transferMemo").asText());assertEquals(1000,qr.path("amount").asInt());assertTrue(qr.path("qrPayload").asText().startsWith("000201"));assertTrue(qr.path("qrPayload").asText().contains("3419120803"));assertTrue(qr.path("qrPayload").asText().contains(tuition));
        read(student,API+"/contracts/"+contract+"/qr?kind=TUITION&amount=1001").andExpect(status().isBadRequest());
        var p=submission(student,contract,"TUITION",100,receipt(student));p.put("transferMemo","NỘI DUNG TỰ ĐỔI");write(student,API+"/contracts/"+contract+"/submissions",p).andExpect(status().isBadRequest());assertEquals(0,transactions(contract));
    }

    @Test void historicalPendingBillRetainsItsMemoSnapshotAfterLearnerNameAndMemoFormatChange()throws Exception{
        String id=submit(student,contract,"TUITION",400);
        String legacy="BKHP "+jdbc.queryForObject("SELECT payment_reference FROM training_contracts WHERE id=?",Long.class,contract);
        jdbc.update("UPDATE student_payment_submissions SET transfer_memo=? WHERE id=?",legacy,UUID.fromString(id));
        student.fullName="Đỗ Thị Hồng";users.saveAndFlush(student);
        var currentGuide=guide(student,contract);String code=String.format(Locale.ROOT,"HV%06d",student.id);
        assertEquals(code,currentGuide.path("contract").path("studentCode").asText());
        assertEquals("DO THI HONG "+code,memo(student,contract,"TUITION"));
        var detail=review(id);assertEquals(legacy,detail.path("expectedTransferMemo").asText());
        assertEquals(legacy,detail.path("submission").path("transferMemo").asText());
        assertTrue(currentGuide.path("submissions").toString().contains(legacy));
        var verification=confirmation(id,"LEGACY-MEMO-"+UUID.randomUUID());
        verification.put("statementMemo",memo(student,contract,"TUITION"));
        write(accountant,API+"/review/"+id+"/confirm",verification).andExpect(status().isBadRequest());
        confirm(id,"LEGACY-MEMO-"+UUID.randomUUID());assertEquals(400,received(contract));assertEquals(0,enrollments());
    }

    @Test void ownershipRolesGrantsAndCsrfProtectGuidesBillsAndReview()throws Exception{
        read(otherStudent,API+"/contracts/"+contract).andExpect(status().isNotFound());
        read(otherStudent,API+"/contracts/"+contract+"/qr?kind=TUITION&amount=100").andExpect(status().isNotFound());
        for(User wrong:List.of(manager,accountant,hr,admin))read(wrong,API+"/contracts/"+contract).andExpect(status().isForbidden());
        var p=submission(student,contract,"TUITION",100,receipt(student));
        mvc.perform(post(API+"/contracts/"+contract+"/submissions").with(user(student.email).roles("STUDENT")).contentType("application/json").content(json.writeValueAsString(p))).andExpect(status().isForbidden());
        String id=body(write(student,API+"/contracts/"+contract+"/submissions",p).andExpect(status().isOk()).andReturn()).path("id").asText();
        read(otherAccountant,API+"/review/"+id).andExpect(status().isNotFound());
        write(student,API+"/review/"+id+"/confirm",confirmation(id,"FORGED-REF")).andExpect(status().isForbidden());
        mvc.perform(post(API+"/review/"+id+"/confirm").with(user(accountant.email).roles("ACCOUNTANT")).contentType("application/json").content(json.writeValueAsString(confirmation(id,"NO-CSRF")))).andExpect(status().isForbidden());
        jdbc.update("DELETE FROM role_function WHERE role_id=3 AND function_code='TuitionDetail_Student'");read(student,API+"/contracts/"+contract).andExpect(status().isForbidden());
    }

    @Test void onlyApprovedProvisionedContractsAcceptWholeDongPaymentsAndClosedHistoryIsReadOnly()throws Exception{
        var valid=submission(student,contract,"TUITION",100,receipt(student));
        for(String field:List.of("signature_approved_at","handed_over_at")){
            jdbc.update("UPDATE training_contracts SET "+field+"=NULL WHERE id=?",contract);
            read(student,API+"/contracts/"+contract).andExpect(status().isConflict());
            write(student,API+"/contracts/"+contract+"/submissions",valid).andExpect(status().isConflict());
            jdbc.update("UPDATE training_contracts SET "+field+"=CURRENT_TIMESTAMP WHERE id=?",contract);
        }
        jdbc.update("UPDATE training_contracts SET status='DRAFT' WHERE id=?",contract);
        write(student,API+"/contracts/"+contract+"/submissions",valid).andExpect(status().isConflict());
        jdbc.update("UPDATE training_contracts SET status='SIGNED' WHERE id=?",contract);
        for(Object amount:List.of(0,1000.5,1001)){
            var invalid=new LinkedHashMap<>(valid);invalid.put("amount",amount);write(student,API+"/contracts/"+contract+"/submissions",invalid).andExpect(status().isBadRequest());
        }
        jdbc.update("UPDATE training_contracts SET status='COMPLETED' WHERE id=?",contract);
        assertFalse(guide(student,contract).path("canSubmit").asBoolean());
        write(student,API+"/contracts/"+contract+"/submissions",valid).andExpect(status().isConflict());
        read(student,API+"/contracts/"+contract+"/qr?kind=TUITION&amount=100").andExpect(status().isConflict());
        assertEquals(0,transactions(contract));assertEquals(0,enrollments());
    }

    @Test void studentCannotUploadOnStaffScreensOrSubmitForeignUnrelatedOrPreviouslyUsedFiles()throws Exception{
        mvc.perform(multipart("/api/workspace/files").file(new MockMultipartFile("file","bill.pdf","application/pdf",PDF)).param("screen","ListPayment").with(user(student.email).roles("STUDENT")).with(csrf())).andExpect(status().isForbidden());
        String foreign=receipt(otherStudent);write(student,API+"/contracts/"+contract+"/submissions",submission(student,contract,"TUITION",100,foreign)).andExpect(status().isBadRequest());
        UUID fake=UUID.randomUUID();write(student,API+"/contracts/"+contract+"/submissions",submission(student,contract,"TUITION",100,fake.toString())).andExpect(status().isBadRequest());
        UUID wrongScreen=UUID.randomUUID();jdbc.update("INSERT INTO workflow_files(id,uploaded_by,filename,content_type,file_size,file_data,upload_screen) VALUES (?,?,'other.pdf','application/pdf',?,?,'AdmissionConsultation')",wrongScreen,student.id,PDF.length,PDF);
        write(student,API+"/contracts/"+contract+"/submissions",submission(student,contract,"TUITION",100,wrongScreen.toString())).andExpect(status().isBadRequest());
        String file=receipt(student);String id=body(write(student,API+"/contracts/"+contract+"/submissions",submission(student,contract,"TUITION",100,file)).andExpect(status().isOk()).andReturn()).path("id").asText();
        write(accountant,API+"/review/"+id+"/mismatch",Map.of("version",0,"reviewNote","Bill chưa đủ thông tin" )).andExpect(status().isOk());
        write(student,API+"/contracts/"+contract+"/submissions",submission(student,contract,"TUITION",100,file)).andExpect(status().isBadRequest());
        mvc.perform(get("/api/workspace/files/"+file).with(user(accountant.email).roles("ACCOUNTANT"))).andExpect(status().isOk());
        mvc.perform(get("/api/workspace/files/"+file).with(user(otherAccountant.email).roles("ACCOUNTANT"))).andExpect(status().isNotFound());
    }

    @Test void aBillRequiresMatchingStatementAmountMemoAndAllVerificationFlags()throws Exception{
        String id=submit(student,contract,"TUITION",1000);var correct=confirmation(id,"CHECK-"+UUID.randomUUID());
        var p=new LinkedHashMap<>(correct);p.put("statementAmount",999);write(accountant,API+"/review/"+id+"/confirm",p).andExpect(status().isBadRequest());
        p=new LinkedHashMap<>(correct);p.put("statementMemo","WRONG CONTRACT MEMO");write(accountant,API+"/review/"+id+"/confirm",p).andExpect(status().isBadRequest());
        p=new LinkedHashMap<>(correct);p.put("identityVerified",false);write(accountant,API+"/review/"+id+"/confirm",p).andExpect(status().isBadRequest());
        assertEquals(0,transactions(contract));assertEquals(0,enrollments());
        p=new LinkedHashMap<>(correct);p.put("statementMemo","  "+correct.get("statementMemo").toString().toLowerCase(Locale.ROOT).replace(" ","   ")+"  ");
        write(accountant,API+"/review/"+id+"/confirm",p).andExpect(status().isOk());assertEquals(1000,received(contract));
    }

    @Test void rejectedBillsRemainUncreditedAndNewUploadsRequireASeparateFreshReview()throws Exception{
        String first=submit(student,contract,"TUITION",1000);var oldConfirmation=confirmation(first,"OLD-"+UUID.randomUUID());
        write(student,API+"/contracts/"+contract+"/submissions",submission(student,contract,"TUITION",1000,receipt(student))).andExpect(status().isConflict());
        write(accountant,API+"/review/"+first+"/mismatch",Map.of("version",0,"reviewNote","Sao kê chưa có khoản chuyển" )).andExpect(status().isOk());
        assertEquals("MISMATCH",review(first).path("submission").path("status").asText());assertTrue(guide(student,contract).path("submissions").toString().contains("Sao kê chưa có khoản chuyển"));
        write(accountant,API+"/review/"+first+"/confirm",oldConfirmation).andExpect(status().isConflict());assertEquals(0,received(contract));assertEquals(0,enrollments());
        String second=submit(student,contract,"TUITION",1000);assertNotEquals(first,second);confirm(second,"NEW-"+UUID.randomUUID());
        assertEquals(1000,received(contract));assertEquals("MISMATCH",review(first).path("submission").path("status").asText());
        write(accountant,API+"/review/"+second+"/confirm",confirmation(second,"REPEAT-"+UUID.randomUUID())).andExpect(status().isConflict());assertEquals(1,transactions(contract));assertActiveContractEnrollment();
    }

    @Test void matchingAnImportedPendingTransactionDoesNotCreateASecondReceipt()throws Exception{
        String id=submit(student,contract,"TUITION",1000),reference="MATCH-PENDING-"+UUID.randomUUID();String tx=imported("TUITION",1000,reference,memo(student,contract,"TUITION"));
        assertEquals(1,transactions(contract));JsonNode confirmed=confirm(id,reference);assertEquals(tx,confirmed.path("transactionId").asText());assertEquals(1,transactions(contract));assertEquals(1000,received(contract));assertActiveContractEnrollment();
    }

    @Test void anAlreadyConfirmedStatementRowCannotCreditTheBillAgain()throws Exception{
        String id=submit(student,contract,"TUITION",1000),reference="MATCH-CONFIRMED-"+UUID.randomUUID();String tx=imported("TUITION",1000,reference,memo(student,contract,"TUITION"));confirmImported(tx);
        assertEquals(1000,received(contract));assertEquals(1,transactions(contract));
        write(accountant,API+"/review/"+id+"/confirm",confirmation(id,reference)).andExpect(status().isConflict());assertEquals(1000,received(contract));assertEquals(1,transactions(contract));assertActiveContractEnrollment();
    }

    @Test void aBankStatementReferenceCannotBeReusedForAnotherContractAndClaimsCannotReserveIt()throws Exception{
        String first=submit(student,contract,"TUITION",1000),reference="UNIQUE-"+UUID.randomUUID();confirm(first,reference);
        UUID secondContract=contract(otherStudent,otherManager,accountant,1000,200);String second=submit(otherStudent,secondContract,"TUITION",1000);
        write(accountant,API+"/review/"+second+"/confirm",confirmation(second,reference)).andExpect(status().isConflict());assertEquals(0,received(secondContract));
        var claim=submission(otherStudent,secondContract,"DEPOSIT",100,receipt(otherStudent));claim.put("declaredBankReference","STUDENT-CLAIM-ONLY");
        write(otherStudent,API+"/contracts/"+secondContract+"/submissions",claim).andExpect(status().isOk());
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM financial_transactions WHERE bank_reference='STUDENT-CLAIM-ONLY'",Integer.class));
    }

    @Test void depositsAloneDoNotEnrollAndZeroTuitionNeedsAccountantActivation()throws Exception{
        String deposit=submit(student,contract,"DEPOSIT",200);confirm(deposit,"DEPOSIT-"+UUID.randomUUID());assertEquals(0,received(contract));assertEquals("SIGNED",statusOfContract(contract));assertEquals(0,enrollments());
        jdbc.update("UPDATE training_contracts SET tuition_due=0 WHERE id=?",contract);
        assertEquals(0,enrollments());var contractView=body(read(accountant,"/api/workspace/screens/TuitionDetail/"+contract).andExpect(status().isOk()).andReturn());
        write(accountant,"/api/workspace/screens/TuitionDetail/"+contract+"/actions/activate-zero",values("version",contractView.path("record").path("version").asLong(),"changeNote","Miễn học phí theo hợp đồng")).andExpect(status().isOk());assertActiveContractEnrollment();
    }

    @Test void oneCombinedBankTransferCreditsSeparateTuitionAndDepositOnlyAfterAccountantConfirmation()throws Exception{
        var qr=body(read(student,API+"/contracts/"+contract+"/qr?kind=COMBINED&amount=1200").andExpect(status().isOk()).andReturn());
        assertEquals(1200,qr.path("amount").asInt());assertEquals(memo(student,contract,"TUITION"),qr.path("transferMemo").asText());
        String id=submitCombined(1000,200),reference="COMBINED-"+UUID.randomUUID();
        assertEquals(0,transactions(contract));assertEquals("SIGNED",statusOfContract(contract));assertEquals(0,enrollments());
        JsonNode pending=review(id).path("submission");assertEquals("COMBINED",pending.path("kind").asText());assertEquals(1200,pending.path("amount").asInt());assertEquals(1000,pending.path("tuitionAmount").asInt());assertEquals(200,pending.path("depositAmount").asInt());
        confirm(id,reference);assertEquals(2,transactions(contract));assertEquals(1000,received(contract));
        var result=guide(student,contract).path("contract");assertEquals(200,result.path("depositPaid").asInt());assertEquals(0,result.path("tuitionRemaining").asInt());assertEquals(0,result.path("depositRemaining").asInt());
        assertEquals("ACTIVE",statusOfContract(contract));assertActiveContractEnrollment();
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM financial_transactions WHERE bank_reference=?",Integer.class,reference));
        assertEquals(1200,jdbc.queryForObject("SELECT SUM(amount) FROM financial_transactions WHERE contract_id=? AND status='CONFIRMED'",Integer.class,contract));
        JsonNode reviewed=review(id).path("submission");assertFalse(reviewed.path("transactionId").asText().isBlank());assertFalse(reviewed.path("depositTransactionId").asText().isBlank());assertNotEquals(reviewed.path("transactionId").asText(),reviewed.path("depositTransactionId").asText());
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM financial_transactions WHERE id=? AND kind='DEPOSIT' AND amount=200 AND bank_reference IS NULL AND status='CONFIRMED'",Integer.class,UUID.fromString(reviewed.path("depositTransactionId").asText())));
        String secondaryId=reviewed.path("depositTransactionId").asText();
        JsonNode secondary=body(read(accountant,"/api/workspace/screens/PaymentDetail/"+secondaryId).andExpect(status().isOk()).andReturn()).path("record");
        assertEquals(reference,secondary.path("bankReference").asText());assertTrue(secondary.path("rawBankReference").isNull());
        for(String action:List.of("confirm","mismatch","resolve")){
            String screen=action.equals("resolve")?"PaymentDetail_Mismatch":"PaymentDetail";
            write(accountant,"/api/workspace/screens/"+screen+"/"+secondaryId+"/actions/"+action,values("version",secondary.path("version").asInt(),"memo","Already verified","changeNote","Already verified","identityVerified",true,"amountVerified",true,"referenceVerified",true)).andExpect(status().isConflict());
        }
        write(accountant,API+"/review/"+id+"/confirm",confirmation(id,reference)).andExpect(status().isConflict());assertEquals(2,transactions(contract));
        UUID other=contract(otherStudent,otherManager,accountant,1000,200);String duplicate=submit(otherStudent,other,"DEPOSIT",200);
        write(accountant,API+"/review/"+duplicate+"/confirm",confirmation(duplicate,reference)).andExpect(status().isConflict());assertEquals(0,transactions(other));
    }

    @Test void combinedClaimsValidateBothAllocationsAndBlockOverlappingPendingBills()throws Exception{
        var input=combinedSubmission(400,50);
        for(Map<String,Object> invalid:List.of(values("amount",451),values("tuitionAmount",0,"depositAmount",450),values("tuitionAmount",1001,"depositAmount",50,"amount",1051),values("tuitionAmount",400,"depositAmount",201,"amount",601),values("tuitionAmount",399.5,"depositAmount",50.5))){
            var p=new LinkedHashMap<>(input);p.putAll(invalid);write(student,API+"/contracts/"+contract+"/submissions",p).andExpect(status().isBadRequest());
        }
        String id=body(write(student,API+"/contracts/"+contract+"/submissions",input).andExpect(status().isOk()).andReturn()).path("id").asText();
        for(String kind:List.of("TUITION","DEPOSIT"))write(student,API+"/contracts/"+contract+"/submissions",submission(student,contract,kind,50,receipt(student))).andExpect(status().isConflict());
        write(student,API+"/contracts/"+contract+"/submissions",combinedSubmission(100,50)).andExpect(status().isConflict());
        write(accountant,API+"/review/"+id+"/mismatch",values("version",0,"reviewNote","Chưa thấy giao dịch trên sao kê")).andExpect(status().isOk());assertEquals(0,transactions(contract));
        String tuition=submit(student,contract,"TUITION",100);
        write(student,API+"/contracts/"+contract+"/submissions",combinedSubmission(100,50)).andExpect(status().isConflict());
        assertEquals("PENDING",review(tuition).path("submission").path("status").asText());
    }

    @Test void importedPendingCombinedTotalIsSplitWithoutRecordingTheTransferTwice()throws Exception{
        String id=submitCombined(400,50),reference="COMBINED-IMPORT-"+UUID.randomUUID();String imported=imported("TUITION",450,reference,memo(student,contract,"TUITION"));
        assertEquals(1,transactions(contract));var result=confirm(id,reference);assertEquals(imported,result.path("transactionId").asText());
        assertEquals(2,transactions(contract));assertEquals(400,received(contract));assertEquals(50,guide(student,contract).path("contract").path("depositPaid").asInt());
        assertEquals(450,jdbc.queryForObject("SELECT SUM(amount) FROM financial_transactions WHERE contract_id=? AND status='CONFIRMED'",Integer.class,contract));
        assertEquals(400,jdbc.queryForObject("SELECT amount FROM financial_transactions WHERE bank_reference=?",Integer.class,reference));
        assertEquals("SIGNED",statusOfContract(contract));assertEquals(0,enrollments());
    }

    @Test void combinedReviewIsAtomicWhenAnotherPaymentAlreadyFilledOneAllocation()throws Exception{
        String combined=submitCombined(1000,200);String deposit=imported("DEPOSIT",200,"OTHER-DEPOSIT-"+UUID.randomUUID(),memo(student,contract,"DEPOSIT"));confirmImported(deposit);
        write(accountant,API+"/review/"+combined+"/confirm",confirmation(combined,"STALE-COMBINED-"+UUID.randomUUID())).andExpect(status().isConflict());
        assertEquals(1,transactions(contract));assertEquals(0,received(contract));assertEquals("PENDING",review(combined).path("submission").path("status").asText());assertEquals("SIGNED",statusOfContract(contract));assertEquals(0,enrollments());
    }
}

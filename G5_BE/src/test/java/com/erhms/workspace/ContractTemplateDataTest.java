package com.erhms.workspace;

import com.erhms.role.entity.Role;
import com.erhms.user.entity.User;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ContractTemplateDataTest {
    JdbcTemplate jdbc;WorkflowData data;ObjectMapper json;ContractTemplateData templates;User manager,hr;

    @BeforeEach void setup(){
        jdbc=mock(JdbcTemplate.class);data=spy(new WorkflowData(jdbc));json=new ObjectMapper();templates=new ContractTemplateData(data,json);
        manager=actor("MANAGER",6);hr=actor("HR",7);
    }
    User actor(String code,long id){var actor=new User();actor.id=id;actor.fullName="Người phụ trách";actor.role=new Role(code,code);return actor;}
    Map<String,Object> record(){return ContractTemplateFixtures.record(Map.of());}

    @Test void revisedTemplateUsesItsFixedValuesAndIgnoresHistoricalFieldsWhenCalculatingTheAmount(){
        var record=record();
        record.put("templateData",Map.of("centerAddress","Địa chỉ bị thay đổi","centerTaxCode","000","centerBankAccount","Tài khoản khác","otherChargesDue",200000,"module1Name","Mô đun cũ","hasGuardian",true,"initialTotal",1,"studentName","Tên giả","agreementPlace","Hà Nội"));
        var values=templates.values(record,hr);
        assertEquals("Nguyễn Anh Đức",values.get("studentName"));assertEquals("HD-MAU-2026",values.get("contractNumber"));
        assertEquals("Hà Nội",values.get("centerAddress"));assertEquals("12423453246134123",values.get("centerTaxCode"));
        assertEquals("999912082003",values.get("centerBankAccount"));assertEquals("Ducnahe176071@fpt.edu.vn",values.get("complaintContactAddress"));
        assertEquals(new BigDecimal("14000000"),values.get("initialTotal"));assertEquals("Mười bốn triệu đồng",values.get("initialTotalInWords"));
        for(String removed:List.of("otherChargesDue","module1Name","hasGuardian","grossTuition","studentReference"))assertFalse(values.containsKey(removed),removed);
        assertEquals("BK-VTCOMS-2026-02",templates.metadata().get("version"));
    }

    @Test void incompleteDraftsRemainValidButUnknownKeysAndInvalidActiveFieldsAreRejected(){
        UUID id=UUID.randomUUID();doReturn(record()).when(data).record("contracts",id.toString(),manager);
        assertDoesNotThrow(()->templates.save(id,Map.of("templateData",Map.of("agreementPlace","Hà Nội")),manager));
        var unknown=assertThrows(WorkflowException.class,()->templates.save(id,Map.of("templateData",Map.of("untrustedSetting","x")),manager));
        assertTrue(unknown.fields.containsKey("templateData.untrustedSetting"));
        for(var invalid:List.of(Map.of("studentDateOfBirth","2026-02-30"),Map.of("studentAddress",42),Map.of("academicContact",false),Map.of("agreementPlace","Địa điểm"+(char)0+"không hợp lệ"),Map.of("endsOn","not-a-date"))){
            var failure=assertThrows(WorkflowException.class,()->templates.save(id,Map.of("templateData",invalid),manager));
            assertTrue(failure.fields.containsKey("templateData."+invalid.keySet().iterator().next()));
        }
    }

    @Test void draftReplacementCannotPersistForgedPrefilledRootOrCalculatedValues()throws Exception{
        UUID id=UUID.randomUUID();var record=record();record.put("templateData","{\"studentIdentityNumber\":\"Old student identity\"}");doReturn(record).when(data).record("contracts",id.toString(),manager);
        templates.save(id,Map.of("templateData",Map.of("agreementPlace","Hà Nội","centerAddress","Địa chỉ giả","centerEmail",Map.of("malicious","ignored"),"studentName","Tên giả","initialTotal",1)),manager);
        var encoded=ArgumentCaptor.forClass(String.class);
        verify(jdbc).update(eq("UPDATE training_contracts SET template_data=? WHERE id=?"),encoded.capture(),eq(id));
        var stored=json.readTree(encoded.getValue());assertEquals("Hà Nội",stored.path("agreementPlace").asText());
        for(String absent:List.of("studentIdentityNumber","centerAddress","centerEmail","studentName","initialTotal"))assertFalse(stored.has(absent),absent);
    }

    @Test void revisedPdfRequiresOnlyRemainingBlanksAndDoesNotRequireRemovedProgramGuardianOrEquipmentData(){
        var details=ContractTemplateFixtures.completeDetails(templates);details.put("module1Name","Thông tin lịch sử");details.put("otherChargesDue",999999);details.put("hasGuardian",true);
        var record=record();record.put("templateData",details);var effective=templates.values(record,hr);
        assertDoesNotThrow(()->templates.requireComplete(effective));
        assertFalse(effective.containsKey("module1Name"));assertFalse(effective.containsKey("hasGuardian"));
        effective.put("studentIdentityNumber","");effective.put("endsOn","2026-11-01");
        var failure=assertThrows(WorkflowException.class,()->templates.requireComplete(effective));
        assertTrue(failure.fields.containsKey("templateData.studentIdentityNumber"));assertTrue(failure.fields.containsKey("templateData.endsOn"));
        assertTrue(failure.fields.keySet().stream().noneMatch(key->key.contains("center")||key.contains("module")||key.contains("guardian")||key.contains("equipment")));
    }

    @Test void vietnameseMoneyWordsHandleMillionsZeroAndLowerGroups(){
        assertEquals("Không đồng",ContractTemplateData.amountInWords(BigDecimal.ZERO));
        assertEquals("Hai mươi mốt triệu chín trăm nghìn đồng",ContractTemplateData.amountInWords(new BigDecimal("21900000")));
        assertEquals("Một triệu không trăm linh năm đồng",ContractTemplateData.amountInWords(new BigDecimal("1000005")));
    }
}
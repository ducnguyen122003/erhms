package com.erhms;

import com.erhms.role.repository.RoleRepository;
import com.erhms.user.entity.User;
import com.erhms.user.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:equipment_asset_batches;MODE=PostgreSQL;DB_CLOSE_DELAY=-1","logging.level.root=WARN","debug=false"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class EquipmentAssetIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @Autowired UserRepository users;
    @Autowired RoleRepository roles;
    User it;
    String assetName;

    @BeforeEach void setup() {
        assetName="Kiểm thử lô thiết bị "+UUID.randomUUID();
        it=new User();it.email=UUID.randomUUID()+"@asset.test";it.fullName="IT thiết bị";it.passwordHash="test-only";
        it.role=roles.findByCode("IT_SUPPORT").orElseThrow();it=users.saveAndFlush(it);
    }

    @AfterEach void cleanup() {
        jdbc.update("DELETE FROM equipment_assets WHERE name=?",assetName);
        users.deleteById(it.id);
    }

    Map<String,Object> input(String code,Object quantity) {
        var p=new LinkedHashMap<String,Object>();
        p.put("code",code);p.put("quantity",quantity);p.put("name",assetName);p.put("purchasedOn","2026-11-01");
        p.put("recordedValue",1500000);p.put("accessories","Dây kết nối");return p;
    }

    ResultActions write(String screen,String id,Map<String,Object> p)throws Exception {
        return mvc.perform(post("/api/workspace/screens/"+screen+(id==null?"":"/"+id))
                .with(user(it.email).roles("IT_SUPPORT")).with(csrf()).contentType("application/json").content(json.writeValueAsString(p)));
    }

    JsonNode save(Map<String,Object> p)throws Exception {
        return json.readTree(write("AddAsset",null,p).andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    List<String> codes() {
        return jdbc.queryForList("SELECT code FROM equipment_assets WHERE name=? ORDER BY code",String.class,assetName);
    }

    @Test void createsIndependentDevicesWithSharedInformationAndDefaults()throws Exception {
        var result=save(input("BK001",3));
        assertEquals("ListAsset",result.path("screen").asText());assertEquals(3,result.path("createdCount").asInt());
        assertEquals(List.of("BK001","BK002","BK003"),codes());
        assertEquals(3,jdbc.queryForObject("SELECT COUNT(DISTINCT id) FROM equipment_assets WHERE name=?",Integer.class,assetName));
        assertEquals(3,jdbc.queryForObject("SELECT COUNT(*) FROM equipment_assets WHERE name=? AND status='AVAILABLE' AND asset_condition IS NULL AND purchased_on=DATE '2026-11-01' AND recorded_value=1500000 AND accessories='Dây kết nối'",Integer.class,assetName));
        assertEquals(0,new BigDecimal("4500000").compareTo(jdbc.queryForObject("SELECT SUM(recorded_value) FROM equipment_assets WHERE name=?",BigDecimal.class,assetName)));
    }

    @Test void acceptsTheReportedThirtyDeviceFormWithoutHiddenRequiredFields()throws Exception {
        var p=input("BK-001",30);
        p.put("purchasedOn","2026-11-15");p.put("accessories","123");
        var result=save(p);
        assertEquals(30,result.path("createdCount").asInt());
        assertEquals(30,codes().size());
        assertEquals("BK-001",codes().get(0));assertEquals("BK-030",codes().get(29));
        assertEquals(30,jdbc.queryForObject("SELECT COUNT(*) FROM equipment_assets WHERE name=? AND status='AVAILABLE' AND asset_condition IS NULL AND purchased_on=DATE '2026-11-15' AND recorded_value=1500000 AND accessories='123'",Integer.class,assetName));
    }

    @Test void prefixesStartAt001AndNumbersContinueAcrossThreeDigits()throws Exception {
        save(input("kit-",2));save(input("BK999",2));save(input("EQ0009",2));
        assertTrue(codes().containsAll(List.of("kit-001","kit-002","BK999","BK1000","EQ0009","EQ0010")));
        var result=save(input("ONE",1));assertEquals("AssetDetail",result.path("screen").asText());
        assertTrue(codes().contains("ONE001"));
    }

    @Test void supportsTheMaximumBatchSize()throws Exception {
        var result=save(input("MAX",500));assertEquals(500,result.path("createdCount").asInt());
        assertEquals(500,codes().size());assertTrue(codes().containsAll(List.of("MAX001","MAX500")));
    }

    @Test void duplicateAnywhereInTheRangeRejectsTheWholeBatch()throws Exception {
        save(input("BK003",1));
        write("AddAsset",null,input("BK001",3)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.fields.code").exists());
        assertEquals(List.of("BK003"),codes());
    }

    @Test void databaseFailureAfterEarlierInsertsRollsBackTheWholeBatch()throws Exception {
        jdbc.execute("ALTER TABLE equipment_assets ADD CONSTRAINT test_asset_batch_failure CHECK (code <> 'FAIL003')");
        try {
            write("AddAsset",null,input("FAIL001",3)).andExpect(status().isConflict());
            assertTrue(codes().isEmpty());
        } finally {
            jdbc.execute("ALTER TABLE equipment_assets DROP CONSTRAINT test_asset_batch_failure");
        }
    }

    @Test void invalidQuantitiesAndCodesDoNotInsertDevices()throws Exception {
        for(Object quantity:List.of(0,-1,501,1.5,"abc",""))
            write("AddAsset",null,input("BK001",quantity)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.fields.quantity").exists());
        write("AddAsset",null,input("BK001",null)).andExpect(status().isBadRequest());
        for(String code:List.of("123","-001","BK000","A".repeat(38),"A"+"9".repeat(39)))
            write("AddAsset",null,input(code,2)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.fields.code").exists());
        assertTrue(codes().isEmpty());
    }

    @Test void legacySingleCreationAndEditingStillAffectOnlyOneDevice()throws Exception {
        var legacy=input("LEGACY-ASSET",1);legacy.remove("quantity");legacy.put("assetCondition","Tốt");
        var result=save(legacy);assertEquals("AssetDetail",result.path("screen").asText());
        assertEquals(List.of("LEGACY-ASSET"),codes());
        save(input("BK",2));
        legacy.put("code","LEGACY-EDITED");legacy.put("status","MAINTENANCE");legacy.put("version",0);
        write("EditAsset",result.path("id").asText(),legacy).andExpect(status().isOk());
        assertEquals(List.of("BK001","BK002","LEGACY-EDITED"),codes());
        assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM equipment_assets WHERE name=? AND status='AVAILABLE'",Integer.class,assetName));
    }

    @Test void creationUsesAvailableStatusEvenIfAnOldClientSendsAnotherStatus()throws Exception {
        var p=input("BK",2);p.put("status","ASSIGNED");save(p);
        assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM equipment_assets WHERE name=? AND status='AVAILABLE'",Integer.class,assetName));
    }
}

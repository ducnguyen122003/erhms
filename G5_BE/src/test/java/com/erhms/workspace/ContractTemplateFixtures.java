package com.erhms.workspace;

import java.math.BigDecimal;
import java.util.*;

final class ContractTemplateFixtures {
    private ContractTemplateFixtures() {}

    @SuppressWarnings("unchecked")
    static Map<String,Object> completeDetails(ContractTemplateData templates){
        Map<String,Object> data=new LinkedHashMap<>();
        for(var section:(List<Map<String,Object>>)templates.metadata().get("sections"))for(var field:(List<Map<String,Object>>)section.get("fields")){
            if(!Boolean.TRUE.equals(field.get("requiredForPdf"))||Boolean.TRUE.equals(field.get("readOnly"))||field.containsKey("condition"))continue;
            String key=field.get("key").toString(),type=field.get("type").toString();
            Object value=switch(type){
                case "date"->"2026-11-01";
                case "number"->new BigDecimal("14200000");
                case "email"->"trungtam@example.test";
                case "select"->((List<?>)field.get("source")).get(0);
                case "checkbox"->false;
                default->"Đã xác nhận theo chương trình đào tạo";
            };
            data.put(key,value);
        }
        data.put("agreementPlace","Hà Nội");
        data.put("studentDateOfBirth","2000-01-02");data.put("studentIdentityNumber","001200000001");
        data.put("studentAddress","Hà Nội");
        data.put("studentExperience","Đã tìm hiểu cấu tạo máy tính.\nChưa có kinh nghiệm sửa chữa chuyên nghiệp.");
        data.put("emergencyContactName","Nguyễn Văn A");data.put("emergencyRelationship","Bố");data.put("emergencyContactPhone","0987654321");
        data.put("endsOn","2027-04-01");data.put("latestOpeningOn","2026-12-01");
        return data;
    }

    static Map<String,Object> record(Map<String,Object> details){
        var record=new LinkedHashMap<String,Object>();
        record.put("code","HD-MAU-2026");record.put("applicantName","Nguyễn Anh Đức");record.put("applicantPhone","0912345678");
        record.put("studentEmail","anhduc@example.test");record.put("registeredCourseName",EnrollmentCourses.NAMES.get(0));record.put("registeredDurationMonths",4);
        record.put("startsOn","2026-12-01");record.put("paymentDueOn","2026-11-30");record.put("tuitionDue",new BigDecimal("13500000"));record.put("depositDue",new BigDecimal("500000"));
        record.put("templateData",details);return record;
    }
}

package com.erhms.workspace;

import com.erhms.user.entity.User;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import static com.erhms.workspace.WorkflowData.*;

/** Validates and stores document snapshots without changing finance or equipment workflows. */
@Service
public class ContractTemplateData {
    private static final ZoneId LOCAL_ZONE=ZoneId.of("Asia/Ho_Chi_Minh");
    private static final BigDecimal MAX_MONEY=new BigDecimal("9999999999999999.99");
    private static final Set<String> ROOT_FIELDS=Set.of("contractNumber","studentName","studentPhone","studentEmail","courseName","durationMonths","startsOn","tuitionDue","depositDue","paymentDueOn","centerName");
    private static final String[] DIGITS={"không","một","hai","ba","bốn","năm","sáu","bảy","tám","chín"};
    private static final String[] GROUPS={"","nghìn","triệu","tỷ","nghìn tỷ","triệu tỷ"};
    private final WorkflowData data;
    private final ObjectMapper json;
    private final Map<String,Object> definition;
    private final Map<String,Object> fixedValues;
    private final Map<String,Map<String,Object>> fields=new LinkedHashMap<>();

    public ContractTemplateData(WorkflowData data,ObjectMapper json){
        this.data=data;this.json=json;
        try(var input=new ClassPathResource("contracts/training-contract-fields.json").getInputStream()){
            definition=json.readValue(input,new TypeReference<LinkedHashMap<String,Object>>(){});
        }catch(IOException failure){throw new IllegalStateException("Không đọc được cấu hình mẫu hợp đồng",failure);}
        fixedValues=json.convertValue(definition.getOrDefault("fixedValues",Map.of()),new TypeReference<LinkedHashMap<String,Object>>(){});
        var receivingBank=PaymentReceivingAccount.destination();
        fixedValues.put("centerBankAccount",receivingBank.get("accountNumber"));
        fixedValues.put("centerBankAccountHolder",receivingBank.get("accountHolder"));
        fixedValues.put("centerBankName",receivingBank.get("bankName"));
        definition.put("fixedValues",new LinkedHashMap<>(fixedValues));
        for(var section:objects(definition.get("sections")))for(var field:objects(section.get("fields"))){
            String key=field.get("key").toString();
            if(fields.putIfAbsent(key,field)!=null)throw new IllegalStateException("Trường mẫu hợp đồng bị trùng: "+key);
        }
    }

    public Map<String,Object> metadata(){return definition;}

    /** Admission defaults never expose an existing profile owned by another Manager. */
    public Map<String,Object> admissionDefaults(Map<String,Object> admission,User actor){
        Map<String,Object> values=new LinkedHashMap<>(fixedValues);
        values.put("agreementDate",LocalDate.now(LOCAL_ZONE).toString());
        values.put("effectiveOn",LocalDate.now(LOCAL_ZONE).toString());
        if(actor!=null&&"MANAGER".equals(actor.getRoleCode())){
            values.put("academicContact",Objects.toString(actor.fullName,""));
            String email=first(admission,"studentEmail","email").strip().toLowerCase(Locale.ROOT);
            if(!email.isEmpty()&&actor.id!=null){
                var profiles=data.rows("SELECT scoped.date_of_birth,scoped.identity_number,scoped.address,scoped.emergency_name,scoped.emergency_phone FROM ("+data.base("students",actor)+") scoped WHERE LOWER(TRIM(scoped.email))=?",email);
                if(profiles.size()==1){
                    var profile=profiles.get(0);
                    put(values,"studentDateOfBirth",profile.get("dateOfBirth"));put(values,"studentIdentityNumber",profile.get("identityNumber"));
                    put(values,"studentAddress",profile.get("address"));put(values,"emergencyContactName",profile.get("emergencyName"));put(values,"emergencyContactPhone",profile.get("emergencyPhone"));
                }
            }
        }
        String name=first(admission,"registeredCourseName","enrollmentCourse","courseName");
        var course=EnrollmentCourses.find(name);
        String start=first(admission,"startsOn");
        if(!start.isEmpty()){
            values.put("latestOpeningOn",start);
            int months=integer(admission.get("registeredDurationMonths"));if(months==0)months=integer(admission.get("durationMonths"));if(months==0&&course!=null)months=course.durationMonths();
            try{if(months>0)values.put("endsOn",LocalDate.parse(start).plusMonths(months).toString());}catch(DateTimeParseException ignored){/* Root contract validation reports an invalid start date. */}
        }
        return values;
    }

    public void attachRecord(Map<String,Object> record,User actor){record.put("templateData",values(record,actor));}

    /** Called after the ordinary contract insert/update within the same transaction. */
    public void save(UUID contractId,Map<String,Object> input,User actor){
        if(!input.containsKey("templateData"))return;
        role(actor,"MANAGER");
        if(!(input.get("templateData") instanceof Map<?,?> submitted))throw invalid("templateData","Dữ liệu mẫu hợp đồng phải là một đối tượng.");
        var record=data.record("contracts",contractId.toString(),actor);
        Map<String,Object> stored=new LinkedHashMap<>();
        for(var entry:submitted.entrySet()){
            if(!(entry.getKey() instanceof String key))throw invalid("templateData","Tên trường mẫu hợp đồng không hợp lệ.");
            // Prefilled template values, root snapshots and calculated totals cannot be overwritten by the client.
            if(fixedValues.containsKey(key)||ROOT_FIELDS.contains(key))continue;
            var field=fields.get(key);
            if(field==null)throw invalid("templateData."+key,"Trường không có trong mẫu hợp đồng.");
            if(Boolean.TRUE.equals(field.get("readOnly")))continue;
            stored.put(key,validateValue(key,entry.getValue(),field));
        }
        try{data.jdbc.update("UPDATE training_contracts SET template_data=? WHERE id=?",json.writeValueAsString(stored),contractId);}
        catch(IOException failure){throw new IllegalStateException("Không lưu được dữ liệu mẫu hợp đồng",failure);}
    }

    /** Returns all values needed by the template, including authoritative root values and totals. */
    public Map<String,Object> values(Map<String,Object> record,User actor){
        Map<String,Object> effective=admissionDefaults(record,actor);
        effective.putAll(stored(record.get("templateData")));
        effective.putAll(fixedValues);
        effective.put("contractNumber",first(record,"code","contractNumber"));
        effective.put("studentName",first(record,"applicantName","studentName","fullName"));
        effective.put("studentPhone",first(record,"applicantPhone","phone","studentPhone"));
        effective.put("studentEmail",first(record,"studentEmail","email"));
        effective.put("courseName",first(record,"registeredCourseName","courseName","enrollmentCourse"));
        effective.put("durationMonths",first(record,"registeredDurationMonths","durationMonths"));
        effective.put("startsOn",first(record,"startsOn"));effective.put("paymentDueOn",first(record,"paymentDueOn"));
        if(record.get("classId")!=null&&Boolean.TRUE.equals(record.get("classPlanFixed"))){
            effective.put("startsOn",first(record,"classStartsOn","startsOn"));
            effective.put("endsOn",first(record,"classEndsOn"));
            effective.put("latestOpeningOn",first(record,"classStartsOn","startsOn"));
        }
        effective.put("tuitionDue",decimal(record.get("tuitionDue")));effective.put("depositDue",decimal(record.get("depositDue")));
        compute(effective);
        return effective;
    }

    public void requireComplete(Map<String,Object> effective){
        Map<String,String> errors=new LinkedHashMap<>();
        for(var field:fields.values()){
            String key=field.get("key").toString();
            if(Boolean.TRUE.equals(field.get("requiredForPdf"))&&blank(effective.get(key)))
                errors.put("templateData."+key,"Điền "+field.get("label").toString().toLowerCase(Locale.ROOT)+" trước khi xuất PDF.");
        }
        for(String key:List.of("studentName","studentPhone","studentEmail","courseName","durationMonths","startsOn"))
            if(blank(effective.get(key)))errors.put("templateData."+key,"Thông tin hợp đồng còn thiếu.");
        if(integer(effective.get("durationMonths"))<=0)errors.put("templateData.durationMonths","Thời lượng khóa học phải lớn hơn 0.");
        compareDates(effective,errors,"startsOn","endsOn","Ngày kết thúc phải từ ngày bắt đầu trở đi.");
        compareDates(effective,errors,"startsOn","latestOpeningOn","Ngày khai giảng muộn nhất phải từ ngày bắt đầu dự kiến trở đi.");
        compareDates(effective,errors,"latestOpeningOn","endsOn","Ngày kết thúc phải từ ngày khai giảng muộn nhất trở đi.");
        if(!errors.isEmpty())throw new WorkflowException(HttpStatus.BAD_REQUEST,"VALIDATION","Hoàn tất các thông tin còn thiếu hoặc chưa phù hợp trước khi xuất PDF.",errors);
    }

    private Object validateValue(String key,Object raw,Map<String,Object> field){
        String path="templateData."+key,type=field.get("type").toString();
        if(type.equals("checkbox")){
            if(!(raw instanceof Boolean))throw invalid(path,"Lựa chọn phải là đúng hoặc sai.");
            return raw;
        }
        if(type.equals("number")){
            if(raw==null||raw instanceof String text&&text.isBlank())return "";
            if(!(raw instanceof Number)&&!(raw instanceof String))throw invalid(path,"Nhập một số hợp lệ.");
            try{
                BigDecimal number=new BigDecimal(raw.toString().strip());
                BigDecimal max=Boolean.TRUE.equals(field.get("money"))?MAX_MONEY:new BigDecimal(Objects.toString(field.getOrDefault("max",100000)));
                if(number.signum()<0||number.scale()>2||number.compareTo(max)>0)throw new NumberFormatException();
                return number;
            }catch(NumberFormatException failure){throw invalid(path,"Nhập số không âm hợp lệ, tối đa 2 chữ số thập phân.");}
        }
        if(raw!=null&&!(raw instanceof String))throw invalid(path,"Thông tin phải là văn bản.");
        String text=raw==null?"":((String)raw).strip();
        int max=field.get("maxLength") instanceof Number length?length.intValue():255;
        if(text.length()>max)throw invalid(path,"Thông tin vượt quá "+max+" ký tự.");
        for(int i=0;i<text.length();i++)if(Character.isISOControl(text.charAt(i))&&text.charAt(i)!='\n'&&text.charAt(i)!='\r'&&text.charAt(i)!='\t')throw invalid(path,"Thông tin có ký tự điều khiển không hợp lệ.");
        if(type.equals("date")&&!text.isEmpty()){
            try{
                LocalDate date=LocalDate.parse(text);
                if(date.getYear()<1900||date.getYear()>2200)throw new DateTimeParseException("range",text,0);
                if(key.equals("studentDateOfBirth")&&date.isAfter(LocalDate.now(LOCAL_ZONE)))throw invalid(path,"Ngày sinh không được ở tương lai.");
            }catch(DateTimeParseException failure){throw invalid(path,"Nhập ngày hợp lệ theo định dạng năm-tháng-ngày.");}
        }
        if(type.equals("email")&&!text.isEmpty()&&!text.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+"))throw invalid(path,"Email không hợp lệ.");
        if(type.equals("select")&&!text.isEmpty()&&field.get("source") instanceof List<?> choices&&!choices.contains(text))throw invalid(path,"Lựa chọn không có trong danh sách.");
        return text;
    }

    private void compute(Map<String,Object> values){
        BigDecimal total=decimal(values.get("tuitionDue")).add(decimal(values.get("depositDue")));
        values.put("initialTotal",total);values.put("initialTotalInWords",amountInWords(total));
    }

    private Map<String,Object> stored(Object raw){
        if(raw==null)return new LinkedHashMap<>();
        try{
            Map<String,Object> parsed;
            if(raw instanceof Map<?,?> map){parsed=new LinkedHashMap<>();for(var entry:map.entrySet())if(entry.getKey() instanceof String key)parsed.put(key,entry.getValue());}
            else if(raw instanceof String text&&text.isBlank())return new LinkedHashMap<>();
            else if(raw instanceof String text)parsed=json.readValue(text,new TypeReference<LinkedHashMap<String,Object>>(){});
            else throw new IllegalStateException("Dữ liệu mẫu hợp đồng không hợp lệ");
            Map<String,Object> values=new LinkedHashMap<>();
            for(var entry:parsed.entrySet())if(fields.containsKey(entry.getKey())&&!fixedValues.containsKey(entry.getKey())&&!Boolean.TRUE.equals(fields.get(entry.getKey()).get("readOnly")))values.put(entry.getKey(),entry.getValue());
            return values;
        }catch(IOException failure){throw new IllegalStateException("Không đọc được dữ liệu mẫu hợp đồng",failure);}
    }

    private static void compareDates(Map<String,Object> values,Map<String,String> errors,String lower,String upper,String message){
        if(blank(values.get(lower))||blank(values.get(upper)))return;
        try{if(LocalDate.parse(values.get(upper).toString()).isBefore(LocalDate.parse(values.get(lower).toString())))errors.put("templateData."+upper,message);}
        catch(DateTimeParseException ignored){errors.put("templateData."+upper,"Ngày không hợp lệ.");}
    }

    @SuppressWarnings("unchecked") private static List<Map<String,Object>> objects(Object value){return (List<Map<String,Object>>)value;}
    private static boolean blank(Object value){return value==null||value instanceof String text&&text.isBlank();}
    private static void put(Map<String,Object> values,String key,Object value){if(value!=null)values.put(key,value);}
    private static String first(Map<String,Object> values,String...keys){for(String key:keys){Object value=values.get(key);if(!blank(value))return value.toString();}return "";}
    private static int integer(Object value){try{return new BigDecimal(Objects.toString(value,"0")).intValueExact();}catch(ArithmeticException|NumberFormatException ignored){return 0;}}
    private static BigDecimal decimal(Object value){try{String raw=Objects.toString(value,"0");return new BigDecimal(raw.isBlank()?"0":raw);}catch(NumberFormatException ignored){return BigDecimal.ZERO;}}

    public static String amountInWords(BigDecimal amount){
        long integer=amount.setScale(0,RoundingMode.DOWN).longValueExact();
        String words=integerWords(integer);
        int fraction=amount.subtract(BigDecimal.valueOf(integer)).movePointRight(2).setScale(0,RoundingMode.HALF_UP).intValue();
        if(fraction>0)words+=" phẩy "+DIGITS[fraction/10]+" "+DIGITS[fraction%10];
        return Character.toUpperCase(words.charAt(0))+words.substring(1)+" đồng";
    }

    private static String integerWords(long value){
        if(value==0)return DIGITS[0];
        List<Integer> groups=new ArrayList<>();while(value>0){groups.add((int)(value%1000));value/=1000;}
        List<String> result=new ArrayList<>();boolean higher=false;
        for(int i=groups.size()-1;i>=0;i--){int group=groups.get(i);if(group==0)continue;String text=threeDigits(group,higher);if(i>0)text+=" "+GROUPS[i];result.add(text);higher=true;}
        return String.join(" ",result);
    }

    private static String threeDigits(int value,boolean full){
        List<String> parts=new ArrayList<>();int hundreds=value/100,tens=value/10%10,ones=value%10;
        if(hundreds>0||full){parts.add(DIGITS[hundreds]);parts.add("trăm");}
        if(tens==0){if(ones>0&&(hundreds>0||full))parts.add("linh");}
        else if(tens==1)parts.add("mười");else{parts.add(DIGITS[tens]);parts.add("mươi");}
        if(ones>0)parts.add(ones==1&&tens>1?"mốt":ones==5&&tens>0?"lăm":DIGITS[ones]);
        return String.join(" ",parts);
    }
}

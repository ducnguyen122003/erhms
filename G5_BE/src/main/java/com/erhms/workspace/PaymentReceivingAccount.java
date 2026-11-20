package com.erhms.workspace;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Component;
import static com.erhms.workspace.WorkflowData.*;

/** Bank details supplied by the centre; VietQR payloads are generated locally. */
@Component
public class PaymentReceivingAccount {
    public Map<String,Object> details(){
        return destination();
    }

    public static Map<String,Object> destination(){
        return Map.of("bankCode","BIDV","bankBin","970418","bankName","BIDV - PGD Hàm Yên",
                "accountNumber","3419120803","accountHolder","NGUYEN ANH DUC");
    }

    public String memo(Map<String,Object> contract,String kind){
        if(!kind.equals("TUITION")&&!kind.equals("DEPOSIT")&&!kind.equals("COMBINED"))throw invalid("kind","Chọn học phí hoặc tiền cọc.");
        String code=Objects.toString(contract.get("studentCode"),"");
        if(!code.matches("HV[0-9]{6,19}"))throw conflict("Học viên chưa có mã học viên hợp lệ. Hãy tải lại sau khi cập nhật hệ thống.");
        String rawName=Objects.toString(contract.get("accountStudentName"),Objects.toString(contract.get("studentName"),""));
        String name=Normalizer.normalize(rawName,Normalizer.Form.NFD).replaceAll("\\p{M}+","")
                .replace('đ','d').replace('Đ','D').toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9 ]"," ").strip().replaceAll(" +"," ");
        if(name.isEmpty())throw conflict("Tên học viên cần có ký tự Latin để tạo nội dung chuyển khoản không dấu.");
        return bankName(name,25-code.length()-1)+" "+code;
    }

    private static String bankName(String name,int budget){
        if(name.length()<=budget)return name;
        var parts=new ArrayList<>(java.util.List.of(name.split(" ")));
        // Preserve the full student code. Shorten middle names first, then the
        // family name, keeping the given name where the bank's 25 chars permit.
        for(int i=1;i<parts.size()-1;i++){
            parts.set(i,parts.get(i).substring(0,1));
            String shorter=String.join(" ",parts);if(shorter.length()<=budget)return shorter;
        }
        if(parts.size()==1)return name.substring(0,budget);
        parts.set(0,parts.get(0).substring(0,1));
        String shorter=String.join(" ",parts);if(shorter.length()<=budget)return shorter;
        String given=parts.get(parts.size()-1),family=parts.get(0);
        shorter=family+" "+given;
        return shorter.length()<=budget?shorter:family+" "+given.substring(0,budget-2);
    }

    public String qrPayload(BigDecimal amount,String memo){
        var bank=details();return vietQrPayload(bank.get("bankBin").toString(),bank.get("accountNumber").toString(),amount,memo);
    }

    static String vietQrPayload(String bankBin,String accountNumber,BigDecimal amount,String memo){
        if(bankBin==null||!bankBin.matches("[0-9]{6}")||accountNumber==null||!accountNumber.matches("[0-9]{6,19}"))throw new IllegalArgumentException("Invalid receiving bank account");
        if(amount==null||amount.signum()<=0||amount.stripTrailingZeros().scale()>0||amount.compareTo(new BigDecimal("9999999999999"))>0)throw invalid("amount","Số tiền phải là số nguyên VND lớn hơn 0 và tối đa 13 chữ số.");
        if(memo==null||!memo.matches("[A-Za-z0-9 ]{1,25}"))throw new IllegalArgumentException("Invalid fixed transfer memo");
        String beneficiary=tlv("00",bankBin)+tlv("01",accountNumber);
        String merchant=tlv("00","A000000727")+tlv("01",beneficiary)+tlv("02","QRIBFTTA");
        String payload=tlv("00","01")+tlv("01","12")+tlv("38",merchant)+tlv("53","704")
                +tlv("54",amount.toBigIntegerExact().toString())+tlv("58","VN")+tlv("62",tlv("08",memo))+"6304";
        int crc=0xffff;
        for(byte value:payload.getBytes(StandardCharsets.UTF_8)){
            crc^=(value&0xff)<<8;
            for(int bit=0;bit<8;bit++)crc=((crc&0x8000)!=0?(crc<<1)^0x1021:crc<<1)&0xffff;
        }
        return payload+String.format(Locale.ROOT,"%04X",crc);
    }

    private static String tlv(String tag,String value){
        int size=value.getBytes(StandardCharsets.UTF_8).length;
        if(size>99)throw new IllegalArgumentException("VietQR field is too long");
        return tag+String.format(Locale.ROOT,"%02d",size)+value;
    }
}

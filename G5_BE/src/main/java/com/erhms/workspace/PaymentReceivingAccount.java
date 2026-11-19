package com.erhms.workspace;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
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
        if(!kind.equals("TUITION")&&!kind.equals("DEPOSIT"))throw invalid("kind","Chọn học phí hoặc tiền cọc.");
        Object raw=contract.get("paymentReference");
        if(!(raw instanceof Number reference)||reference.longValue()<=0)throw conflict("Hợp đồng chưa có mã thanh toán. Hãy tải lại sau khi cập nhật hệ thống.");
        return (kind.equals("TUITION")?"BKHP ":"BKCOC ")+reference.longValue();
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

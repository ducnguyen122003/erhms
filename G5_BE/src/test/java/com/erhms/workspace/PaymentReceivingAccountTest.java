package com.erhms.workspace;

import java.math.BigDecimal;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PaymentReceivingAccountTest {
    @Test void vietQrPayloadMatchesPublishedReferenceIncludingChecksum(){
        // Reference response: https://www.vietqr.io/danh-sach-api/link-tao-ma-nhanh/api-tao-ma-qr
        String reference="00020101021238560010A0000007270126000697041501121133666688880208QRIBFTTA53037045405790005802VN62220818Ung Ho Quy Vac Xin63043ACF";
        assertEquals(reference,PaymentReceivingAccount.vietQrPayload("970415","113366668888",new BigDecimal("79000"),"Ung Ho Quy Vac Xin"));
    }

    @Test void receiverMatchesSuppliedBankAndLearnerIdentityIsSharedAcrossPaymentKinds(){
        var account=new PaymentReceivingAccount();var bank=account.details();
        assertEquals("970418",bank.get("bankBin"));assertEquals("3419120803",bank.get("accountNumber"));
        assertEquals("NGUYEN ANH DUC",bank.get("accountHolder"));
        var learner=Map.<String,Object>of("studentCode","HV000123","accountStudentName","Nguyễn Ánh Đức","studentName","Tên trên hợp đồng");
        String tuition=account.memo(learner,"TUITION");
        assertEquals("NGUYEN ANH DUC HV000123",tuition);
        assertEquals(tuition,account.memo(learner,"DEPOSIT"));assertEquals(tuition,account.memo(learner,"COMBINED"));
        assertNotEquals(tuition,account.memo(Map.of("studentCode","HV000124","studentName","Nguyễn Ánh Đức"),"TUITION"));
        String qr=account.qrPayload(new BigDecimal("14200000.00"),tuition);
        assertTrue(qr.contains("970418"));assertTrue(qr.contains("3419120803"));assertTrue(qr.contains("14200000"));assertTrue(qr.contains(tuition));
    }

    @Test void vietnameseNamesAreNormalizedAndLongNamesNeverLoseTheStudentCode(){
        var account=new PaymentReceivingAccount();
        assertEquals("DANG A DUC HV000001",account.memo(Map.of("studentCode","HV000001","studentName","  Đặng-A, Đức!  "),"TUITION"));
        String longName=account.memo(Map.of("studentCode","HV000123","studentName","Nguyễn Thị Ngọc Ánh Đức"),"TUITION");
        assertEquals("NGUYEN T N A DUC HV000123",longName);
        String longCode=account.memo(Map.of("studentCode","HV9223372036854775807","studentName","Nguyễn Ánh Đức"),"DEPOSIT");
        assertTrue(longCode.endsWith(" HV9223372036854775807"));assertTrue(longCode.length()<=25);
        assertTrue(longCode.matches("[A-Z0-9 ]+"));
    }

    @Test void invalidOrFractionalAmountsCannotProduceAMisleadingQr(){
        var account=new PaymentReceivingAccount();
        for(String value:new String[]{"0","-1","0.50","10000000000000"})assertThrows(WorkflowException.class,()->account.qrPayload(new BigDecimal(value),"NGUYEN ANH DUC HV000001"));
        assertThrows(WorkflowException.class,()->account.memo(Map.of(),"TUITION"));
        assertThrows(WorkflowException.class,()->account.memo(Map.of("studentCode","HV000001","studentName","Nguyễn Ánh Đức"),"REFUND"));
    }
}

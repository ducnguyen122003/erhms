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

    @Test void receiverMatchesSuppliedBankAndContractReferencesStayUniqueAndWithinBankMemoLimits(){
        var account=new PaymentReceivingAccount();var bank=account.details();
        assertEquals("970418",bank.get("bankBin"));assertEquals("3419120803",bank.get("accountNumber"));
        assertEquals("NGUYEN ANH DUC",bank.get("accountHolder"));
        String tuition=account.memo(Map.of("paymentReference",Long.MAX_VALUE),"TUITION");
        String deposit=account.memo(Map.of("paymentReference",Long.MAX_VALUE),"DEPOSIT");
        assertNotEquals(tuition,deposit);assertTrue(tuition.length()<=25);assertTrue(deposit.length()<=25);
        assertNotEquals(tuition,account.memo(Map.of("paymentReference",Long.MAX_VALUE-1),"TUITION"));
        String qr=account.qrPayload(new BigDecimal("14200000.00"),"BKHP 12");
        assertTrue(qr.contains("970418"));assertTrue(qr.contains("3419120803"));assertTrue(qr.contains("14200000"));assertTrue(qr.contains("BKHP 12"));
    }

    @Test void invalidOrFractionalAmountsCannotProduceAMisleadingQr(){
        var account=new PaymentReceivingAccount();
        for(String value:new String[]{"0","-1","0.50","10000000000000"})assertThrows(WorkflowException.class,()->account.qrPayload(new BigDecimal(value),"BKHP 1"));
        assertThrows(WorkflowException.class,()->account.memo(Map.of(),"TUITION"));
        assertThrows(WorkflowException.class,()->account.memo(Map.of("paymentReference",1),"REFUND"));
    }
}

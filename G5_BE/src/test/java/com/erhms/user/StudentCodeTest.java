package com.erhms.user;

import java.sql.DriverManager;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class StudentCodeTest {
    @Test
    void codesPadSmallAccountIdsWithoutTruncatingLargerIds() {
        assertNull(StudentCode.fromId(null));
        assertEquals("HV000001", StudentCode.fromId(1L));
        assertEquals("HV000123", StudentCode.fromId(123L));
        assertEquals("HV999999", StudentCode.fromId(999999L));
        assertEquals("HV1000000", StudentCode.fromId(1000000L));
        assertEquals("HV9223372036854775807", StudentCode.fromId(Long.MAX_VALUE));
        assertThrows(IllegalArgumentException.class, () -> StudentCode.fromId(0L));
        assertThrows(IllegalArgumentException.class, () -> StudentCode.fromId(-1L));
    }

    @Test
    void workspaceSqlMatchesTheIdentityUsedByProfileResponses() throws Exception {
        try (var connection = DriverManager.getConnection(
                "jdbc:h2:mem:student_codes_" + UUID.randomUUID() + ";MODE=PostgreSQL", "sa", "");
             var query = connection.prepareStatement("SELECT " + StudentCode.sql("v.student_id")
                     + " AS student_code FROM (SELECT CAST(? AS BIGINT) AS student_id) v")) {
            for (Long id : new Long[]{null, 1L, 123L, 999999L, 1000000L, Long.MAX_VALUE}) {
                query.setObject(1, id);
                try (var result = query.executeQuery()) {
                    assertTrue(result.next());
                    assertEquals(StudentCode.fromId(id), result.getString("student_code"));
                }
            }
        }
    }
}

package com.erhms.workspace;

import java.util.UUID;

/** A contract reserves a seat; its later enrollment must not reserve it twice. */
final class ClassReservations {
    private ClassReservations() {}

    static long occupied(WorkflowData data,UUID classId,UUID excludedContract){
        String contracts="SELECT COUNT(*) FROM training_contracts c WHERE c.class_id=? AND c.status<>'CANCELLED'";
        long reserved=excludedContract==null?data.count(contracts,classId):data.count(contracts+" AND c.id<>?",classId,excludedContract);
        long legacy=data.count("SELECT COUNT(*) FROM training_enrollments e WHERE e.class_id=? AND e.status IN ('ACTIVE','PENDING') AND NOT EXISTS(SELECT 1 FROM training_contracts c WHERE c.id=e.contract_id AND c.class_id=e.class_id AND c.status<>'CANCELLED')",classId);
        return reserved+legacy;
    }
}

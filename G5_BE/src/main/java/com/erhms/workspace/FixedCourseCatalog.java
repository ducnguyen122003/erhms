package com.erhms.workspace;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/** Stable operational identities for the four programs offered by the center. */
public final class FixedCourseCatalog {
    public static final List<UUID> IDS=List.of(
            UUID.fromString("64000000-0000-0000-0000-000000000001"),
            UUID.fromString("64000000-0000-0000-0000-000000000002"),
            UUID.fromString("64000000-0000-0000-0000-000000000003"),
            UUID.fromString("64000000-0000-0000-0000-000000000004"));
    public static final List<String> CODES=List.of("BK-LAPTOP","BK-PC-PRINTER","BK-ELECTRONICS","BK-REFRIGERATION");
    public static final String ID_SQL=IDS.stream().map(id->"'"+id+"'").collect(Collectors.joining(","));
    private FixedCourseCatalog(){}
}

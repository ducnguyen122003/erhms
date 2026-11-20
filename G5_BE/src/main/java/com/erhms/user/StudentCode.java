package com.erhms.user;

import java.util.Locale;

/** Stable student identity derived from the immutable account primary key. */
public final class StudentCode {
    private StudentCode() {}

    public static String fromId(Long id) {
        if (id == null) return null;
        if (id <= 0) throw new IllegalArgumentException("Student account ID must be positive");
        return String.format(Locale.ROOT, "HV%06d", id);
    }

    /** The expression is an application constant, never input received from a request. */
    public static String sql(String idExpression) {
        String digits = "CAST(" + idExpression + " AS VARCHAR)";
        return "CASE WHEN " + idExpression + " IS NULL THEN NULL ELSE 'HV'||LPAD("
                + digits + ",GREATEST(6,LENGTH(" + digits + ")),'0') END";
    }
}

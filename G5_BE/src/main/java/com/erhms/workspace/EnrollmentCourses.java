package com.erhms.workspace;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.List;
import java.util.Locale;

/** The registered programs and their base prices, independent of negotiated contract tuition. */
public final class EnrollmentCourses {
    public record Course(String name,int durationMonths,BigDecimal tuitionFee) {}

    public static final List<Course> CATALOG=List.of(
            new Course("Sửa chữa máy tính Laptop chuyên nghiệp",4,new BigDecimal("14200000")),
            new Course("Sửa chữa máy tính PC & máy in",4,new BigDecimal("13000000")),
            new Course("Sửa chữa Điện tử - Điện gia dụng",4,new BigDecimal("11000000")),
            new Course("Sửa chữa Điện lạnh, Điện gia dụng & Inverter",7,new BigDecimal("21900000")));
    public static final List<String> NAMES=CATALOG.stream().map(Course::name).toList();

    private EnrollmentCourses() {}

    public static String normalized(String name) {
        return Normalizer.normalize(name==null?"":name,Normalizer.Form.NFC).strip().replaceAll("(?U)\\s+"," ").toLowerCase(Locale.ROOT);
    }

    public static Course find(Object name) {
        String key=normalized(name instanceof String value?value:null);
        return CATALOG.stream().filter(course->normalized(course.name()).equals(key)).findFirst().orElse(null);
    }
}

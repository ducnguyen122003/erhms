package com.erhms.workspace;

import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static com.erhms.workspace.WorkflowData.classScheduleScope;
import static com.erhms.workspace.WorkflowData.date;
import static com.erhms.workspace.WorkflowData.invalid;
import static com.erhms.workspace.WorkflowData.uuid;

@RestController
@RequestMapping("/api/workspace/attendance")
public class AttendanceScheduleController {
    private static final ZoneId TIME_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final int PAGE_SIZE = 25;
    private final WorkflowData data;
    private final WorkspaceService workspace;

    public AttendanceScheduleController(WorkflowData data, WorkspaceService workspace) {
        this.data = data;
        this.workspace = workspace;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public Map<String, Object> list(@RequestParam(defaultValue = "") String week,
                                    @RequestParam(defaultValue = "") String classId,
                                    @RequestParam(defaultValue = "ALL") String status,
                                    @RequestParam(defaultValue = "0") int page) {
        var user = workspace.access("ListAttendance");
        if (!Set.of("ALL", "PENDING", "UPCOMING", "FINALIZED", "BACKLOG").contains(status) || page < 0 || page > 100_000) {
            throw invalid("filter", "Bộ lọc điểm danh không hợp lệ.");
        }
        LocalDate today = LocalDate.now(TIME_ZONE);
        LocalDate currentMonday = monday(today);
        LocalDate firstDay = week.isBlank() ? currentMonday : monday(date(Map.of("week", week), "week", true));
        if (firstDay.getYear() < 1000 || firstDay.getYear() > 9998) throw invalid("week", "Tuần học không hợp lệ.");
        OffsetDateTime now = OffsetDateTime.now(TIME_ZONE);
        String classFilter = " FROM training_classes cl WHERE cl.status<>'CANCELLED' AND " + classScheduleScope(user, "cl");
        var classes = data.rows("SELECT cl.id,cl.code,cl.name" + classFilter + " ORDER BY cl.starts_on DESC,cl.code,cl.id");
        UUID selectedClass = classId.isBlank() ? null : uuid(classId);
        if (selectedClass != null) data.one("SELECT cl.id" + classFilter + " AND cl.id=?", selectedClass);

        String source = " FROM training_sessions s JOIN training_classes cl ON cl.id=s.class_id"
                + " LEFT JOIN m_classroom room ON room.id=s.classroom_id WHERE cl.status<>'CANCELLED' AND "
                + classScheduleScope(user, "cl") + (selectedClass == null ? "" : " AND cl.id=?");
        List<Object> scopeArgs = selectedClass == null ? List.of() : List.of(selectedClass);
        String weekly = source + " AND s.starts_at>=? AND s.starts_at<?";
        List<Object> weekArgs = new ArrayList<>(scopeArgs);
        weekArgs.add(startOfDay(firstDay));
        weekArgs.add(startOfDay(firstDay.plusDays(7)));

        List<Object> summaryArgs = new ArrayList<>();
        summaryArgs.add(now);
        summaryArgs.add(now);
        summaryArgs.addAll(weekArgs);
        var counts = data.one("""
                SELECT COUNT(*) AS total,
                  COUNT(CASE WHEN s.attendance_finalized=FALSE AND s.starts_at<=? THEN 1 END) AS pending,
                  COUNT(CASE WHEN s.attendance_finalized=FALSE AND s.starts_at>? THEN 1 END) AS upcoming,
                  COUNT(CASE WHEN s.attendance_finalized=TRUE THEN 1 END) AS finalized
                """ + weekly, summaryArgs.toArray());
        String backlog = source + " AND s.attendance_finalized=FALSE AND s.starts_at<?";
        List<Object> backlogArgs = new ArrayList<>(scopeArgs);
        backlogArgs.add(startOfDay(currentMonday));
        long backlogCount = data.count("SELECT COUNT(*)" + backlog, backlogArgs.toArray());
        counts.put("backlog", backlogCount);

        String filtered = switch (status) {
            case "BACKLOG" -> backlog;
            case "PENDING" -> weekly + " AND s.attendance_finalized=FALSE AND s.starts_at<=?";
            case "UPCOMING" -> weekly + " AND s.attendance_finalized=FALSE AND s.starts_at>?";
            case "FINALIZED" -> weekly + " AND s.attendance_finalized=TRUE";
            default -> weekly;
        };
        long total = ((Number) counts.get(status.equals("BACKLOG") ? "backlog" : status.equals("ALL") ? "total" : status.toLowerCase(java.util.Locale.ROOT))).longValue();
        List<Object> listArgs = new ArrayList<>();
        listArgs.add(now);
        listArgs.addAll(status.equals("BACKLOG") ? backlogArgs : weekArgs);
        if (status.equals("PENDING") || status.equals("UPCOMING")) listArgs.add(now);
        listArgs.add(PAGE_SIZE);
        listArgs.add(page * PAGE_SIZE);
        String order = status.equals("BACKLOG") ? "s.starts_at DESC,s.id" : "s.starts_at,s.id";
        var items = data.rows("""
                SELECT s.id,s.title,s.class_id,s.starts_at,s.ends_at,COALESCE(room.name,s.room) AS room,
                  cl.code AS class_code,cl.name AS class_name,
                  CASE WHEN s.attendance_finalized=TRUE THEN 'FINALIZED'
                       WHEN s.starts_at>? THEN 'UPCOMING' ELSE 'PENDING' END AS attendance_status
                """ + filtered + " ORDER BY " + order + " LIMIT ? OFFSET ?", listArgs.toArray());

        return Map.of("items", items, "classes", classes, "counts", counts, "week", firstDay.toString(),
                "today", today.toString(), "total", total, "page", page, "size", PAGE_SIZE, "totalPages", (total + PAGE_SIZE - 1) / PAGE_SIZE);
    }

    private static LocalDate monday(LocalDate day) {
        return day.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    private static OffsetDateTime startOfDay(LocalDate day) {
        return day.atStartOfDay(TIME_ZONE).toOffsetDateTime();
    }
}

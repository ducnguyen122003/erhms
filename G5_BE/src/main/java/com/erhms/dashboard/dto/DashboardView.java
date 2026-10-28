package com.erhms.dashboard.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record DashboardView(String role, Instant updatedAt, List<Metric> stats, List<Metric> highlights,
                            List<Panel> panels, List<Task> tasks, Chart chart, Progress progress) {
    public record Metric(String key, String label, Number value, String unit, String description, String tone) {}
    public record Panel(String key, String title, String description, List<String> columns, List<Row> rows) {}
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Row(String id, List<String> cells, String status, String statusTone, String detail) {}
    public record Task(String key, String title, String description, long count, String tone) {}
    public record Chart(String title, String description, List<Point> points) {}
    public record Point(String label, long value) {}
    public record Progress(long completed, long total, long absent, String endDate) {}
}

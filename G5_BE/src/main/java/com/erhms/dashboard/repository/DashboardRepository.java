package com.erhms.dashboard.repository;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Read aggregates from persisted operational data. Query failures intentionally propagate. */
@Repository
public class DashboardRepository {
    private final JdbcTemplate jdbc;
    public DashboardRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public long count(String sql, Object... args) {
        return jdbc.queryForObject(sql, Long.class, parameters(args));
    }
    public BigDecimal amount(String sql, Object... args) {
        return jdbc.queryForObject(sql, BigDecimal.class, parameters(args));
    }
    public List<Map<String, Object>> rows(String sql, Object... args) {
        return jdbc.queryForList(sql, parameters(args));
    }
    public void recordLogin(long userId) {
        jdbc.update("INSERT INTO user_login_events(id,user_id,occurred_at) VALUES (?,?,?)",
                UUID.randomUUID(), userId, Timestamp.from(Instant.now()));
    }
    private static Object[] parameters(Object[] args) {
        return Arrays.stream(args).map(arg -> arg instanceof Instant time ? Timestamp.from(time) : arg).toArray();
    }
}

package com.example.TTGDHNDuongAn.service;

import java.sql.Date;
import java.time.LocalDate;
import java.time.ZoneId;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class DashboardSummaryService {
    public record Summary(long totalStudents, long activeStudents, long totalStaff,
                          long activeClasses, long studentsAdmittedThisMonth,
                          long staffJoinedThisMonth, long activeClassTypes) {}

    private final JdbcTemplate jdbc;

    public DashboardSummaryService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Summary getSummary() {
        LocalDate monthStart = LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")).withDayOfMonth(1);
        Date start = Date.valueOf(monthStart);
        Date end = Date.valueOf(monthStart.plusMonths(1));
        return new Summary(
            count("SELECT COUNT(*) FROM student"),
            count("SELECT COUNT(*) FROM student WHERE status='ACTIVE'"),
            count("SELECT COUNT(*) FROM staff"),
            count("SELECT COUNT(*) FROM learning_class WHERE status='ACTIVE'"),
            count("SELECT COUNT(*) FROM student WHERE admitted_on >= ? AND admitted_on < ?", start, end),
            count("SELECT COUNT(*) FROM staff WHERE joined_on >= ? AND joined_on < ?", start, end),
            count("SELECT COUNT(DISTINCT class_type) FROM learning_class WHERE status='ACTIVE'")
        );
    }

    private long count(String sql, Object... args) {
        Long value = jdbc.queryForObject(sql, Long.class, args);
        return value == null ? 0 : value;
    }
}

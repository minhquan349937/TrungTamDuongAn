package com.example.TTGDHNDuongAn.service;

import com.example.TTGDHNDuongAn.security.AppUserPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PlanAccessService {
    private final JdbcTemplate jdbc;
    public PlanAccessService(JdbcTemplate jdbc) { this.jdbc=jdbc; }

    public boolean canReadStudent(String studentId, AppUserPrincipal actor) {
        if ("ADMIN".equals(actor.role())) return true;
        if (!"TEACHER".equals(actor.role())) return false;
        Integer count=jdbc.queryForObject("SELECT COUNT(*) FROM enrollment e JOIN learning_class c ON c.id=e.class_id " +
            "JOIN staff t ON t.id=c.teacher_id JOIN app_user u ON u.id=t.user_id " +
            "WHERE e.student_id=? AND t.user_id=? AND u.status='ACTIVE' AND t.status='ACTIVE' " +
            "AND c.status='ACTIVE' AND e.status='ENROLLED' " +
            "AND e.started_on<=DATE(CONVERT_TZ(UTC_TIMESTAMP(),'+00:00','+07:00')) " +
            "AND (e.ended_on IS NULL OR e.ended_on>DATE(CONVERT_TZ(UTC_TIMESTAMP(),'+00:00','+07:00'))) " +
            "AND c.starts_on<=DATE(CONVERT_TZ(UTC_TIMESTAMP(),'+00:00','+07:00')) " +
            "AND (c.ends_on IS NULL OR c.ends_on>DATE(CONVERT_TZ(UTC_TIMESTAMP(),'+00:00','+07:00')))",
            Integer.class,studentId,actor.id());
        return count!=null && count>0;
    }
    public void requireStudent(String studentId,AppUserPrincipal actor) {
        if(!canReadStudent(studentId,actor))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Bạn chưa được phân công phụ trách học sinh này.");
    }
}

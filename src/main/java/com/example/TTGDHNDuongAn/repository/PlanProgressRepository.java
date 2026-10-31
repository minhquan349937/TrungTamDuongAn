package com.example.TTGDHNDuongAn.repository;
import com.example.TTGDHNDuongAn.dto.PlanProgressDtos.EditPlan;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.time.LocalDate;
@Repository
public class PlanProgressRepository {
    private final JdbcTemplate jdbc;
    public PlanProgressRepository(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    public String lockPlanStudent(String id) {
        return jdbc.queryForList("SELECT student_id FROM learning_plans WHERE id=? FOR UPDATE",String.class,id).stream().findFirst().orElse(null);
    }
    public record State(String name,LocalDate startDate,String status) {}
    public State state(String id) {
        return jdbc.queryForObject("SELECT name,start_date,status FROM learning_plans WHERE id=?",
            (rs,n)->new State(rs.getString("name"),rs.getDate("start_date").toLocalDate(),rs.getString("status")),id);
    }
    public boolean hasEvaluation(String id) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM plan_evaluations WHERE learning_plan_id=?)",Boolean.class,id));
    }
    public void edit(String id,EditPlan request) {
        jdbc.update("UPDATE learning_plans SET name=?,start_date=?,end_date=CASE " +
            "WHEN ?='COMPLETED' THEN IF(status='COMPLETED',COALESCE(end_date,DATE(UTC_TIMESTAMP()+INTERVAL 7 HOUR)),DATE(UTC_TIMESTAMP()+INTERVAL 7 HOUR)) " +
            "WHEN ? IN ('ACTIVE','DRAFT') AND status='COMPLETED' THEN NULL ELSE end_date END,status=? WHERE id=?",
            request.name().trim(),request.startDate(),request.status(),request.status(),request.status(),id);
    }
}

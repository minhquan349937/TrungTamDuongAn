package com.example.TTGDHNDuongAn.repository;

import com.example.TTGDHNDuongAn.dto.InterventionDtos.*;
import com.example.TTGDHNDuongAn.dto.StudentPlanDtos;
import java.sql.Date;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class InterventionRepository {
    private final JdbcTemplate jdbc;
    public InterventionRepository(JdbcTemplate jdbc) { this.jdbc=jdbc; }

    private static final String STUDENTS = "SELECT s.id,s.code,s.full_name,s.birth_date,s.status," +
        "COALESCE(t.studying,0) studying,COALESCE(t.achieved,0) achieved FROM student s LEFT JOIN (" +
        "SELECT lp.student_id,COUNT(DISTINCT CASE WHEN lp.status='ACTIVE' AND pg.status IN ('NOT_STARTED','NOT_ACHIEVED') " +
        "THEN IF(pg.specific_goal_id IS NULL,CONCAT('G:',pg.goal_id),CONCAT('S:',pg.specific_goal_id)) END) studying," +
        "COUNT(DISTINCT CASE WHEN pg.status='ACHIEVED' THEN IF(pg.specific_goal_id IS NULL,CONCAT('G:',pg.goal_id),CONCAT('S:',pg.specific_goal_id)) END) achieved " +
        "FROM learning_plans lp JOIN plan_goals pg ON pg.plan_id=lp.id WHERE lp.status<>'CANCELLED' " +
        "GROUP BY lp.student_id) t ON t.student_id=s.id ";

    public List<StudentSummary> students(String search) {
        return jdbc.query(STUDENTS+"WHERE (?='' OR s.code LIKE CONCAT('%',?,'%') OR s.full_name LIKE CONCAT('%',?,'%')) ORDER BY s.code,s.id",
            (rs,n)->summary(rs),search,search,search);
    }
    public Optional<StudentSummary> student(String id) {
        return jdbc.query(STUDENTS+"WHERE s.id=?",(rs,n)->summary(rs),id).stream().findFirst();
    }
    private StudentSummary summary(java.sql.ResultSet rs) throws java.sql.SQLException {
        var birth=rs.getDate("birth_date").toLocalDate();
        int age=(int)ChronoUnit.MONTHS.between(birth,LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")));
        return new StudentSummary(rs.getString("id"),rs.getString("code"),rs.getString("full_name"),birth,
            rs.getString("status"),Math.max(age,0),rs.getLong("studying"),rs.getLong("achieved"));
    }
    public List<ProgramSummary> programs() {
        // Aggregate each relation before joining: levels/children must not multiply domain or goal totals.
        return jdbc.query("SELECT p.id,p.code,p.name,p.description,COALESCE(dc.total,0) domain_count," +
            "COALESCE(gc.total,0) goal_count,ages.age_min,ages.age_max FROM programs p " +
            "LEFT JOIN (SELECT program_id,COUNT(*) total FROM domains WHERE is_active=1 GROUP BY program_id) dc ON dc.program_id=p.id " +
            "LEFT JOIN (SELECT ld.program_id,COUNT(*) total FROM goals g JOIN program_level_domains ld ON ld.id=g.level_domain_id " +
            "JOIN domains d ON d.id=ld.domain_id WHERE g.is_active=1 AND d.is_active=1 GROUP BY ld.program_id) gc ON gc.program_id=p.id " +
            "LEFT JOIN (SELECT program_id,MIN(age_min_months) age_min,MAX(age_max_months) age_max FROM program_levels GROUP BY program_id) ages ON ages.program_id=p.id " +
            "WHERE p.is_active=1 ORDER BY p.code,p.id",
            (rs,n)->new ProgramSummary(rs.getString("id"),rs.getString("code"),rs.getString("name"),rs.getString("description"),
                rs.getLong("domain_count"),rs.getLong("goal_count"),rs.getObject("age_min",Integer.class),rs.getObject("age_max",Integer.class)));
    }
    public List<DomainRow> domains(String programId) {
        return jdbc.query("SELECT d.code,d.name,d.id,ld.id level_domain_id,pl.id level_id,pl.name level_name,pl.age_min_months,pl.age_max_months " +
            "FROM program_level_domains ld JOIN domains d ON d.id=ld.domain_id JOIN program_levels pl ON pl.id=ld.program_level_id WHERE pl.program_id=? AND d.is_active=1 " +
            "ORDER BY d.display_order,d.id,pl.display_order,pl.id,ld.display_order,ld.id",
            (rs,n)->new DomainRow(rs.getString("code"),rs.getString("name"),new AgeOption(rs.getString("id"),rs.getString("level_id"),
                rs.getString("level_name"),rs.getInt("age_min_months"),rs.getInt("age_max_months"),rs.getString("level_domain_id"))),programId);
    }
    public Optional<String> programCodeForDomain(String id) {
        return jdbc.query("SELECT p.code FROM program_level_domains ld JOIN domains d ON d.id=ld.domain_id JOIN program_levels pl ON pl.id=ld.program_level_id JOIN programs p ON p.id=pl.program_id " +
            "WHERE ld.id=? AND d.is_active=1 AND p.is_active=1",(rs,n)->rs.getString(1),id).stream().findFirst();
    }
    public List<GoalChoice> goals(String domainId,boolean supportsSpecificGoals,boolean isVb) {
        // Denver executes SQL that never references specific_goals, including for choice flags.
        String children=supportsSpecificGoals ? "EXISTS(SELECT 1 FROM specific_goals sg WHERE sg.goal_id=g.id AND sg.is_active=1)" : "FALSE";
        return jdbc.query("SELECT g.id,g.code,g.name,g.description,g.display_order,"+children+" has_children FROM goals g " +
            "WHERE g.level_domain_id=? AND g.is_active=1 ORDER BY g.display_order,g.id",
            (rs,n)->new GoalChoice(rs.getString("id"),rs.getString("code"),rs.getString("name"),rs.getString("description"),
                rs.getInt("display_order"),rs.getBoolean("has_children"),true),domainId);
    }
    public boolean lockStudent(String studentId) {
        return !jdbc.queryForList("SELECT id FROM student WHERE id=? FOR UPDATE",String.class,studentId).isEmpty();
    }
    public record ExistingPlan(StudentPlanDtos.Plan plan,String actorId) { }
    public Optional<ExistingPlan> existing(String id) {
        return jdbc.query(StudentPlanRepository.PLAN_SELECT+"WHERE lp.id=?",
            (rs,n)->new ExistingPlan(StudentPlanRepository.plan(rs),rs.getString("created_by")),id).stream().findFirst();
    }
    public StudentPlanDtos.Plan insert(String studentId,Draft draft,String actorId) {
        jdbc.update("INSERT INTO learning_plans(id,student_id,name,start_date,status,created_by) VALUES(?,?,?,?,'ACTIVE',?)",
            draft.requestId(),studentId,draft.name().trim(),Date.valueOf(draft.startDate()),actorId);
        return existing(draft.requestId()).orElseThrow().plan();
    }
}

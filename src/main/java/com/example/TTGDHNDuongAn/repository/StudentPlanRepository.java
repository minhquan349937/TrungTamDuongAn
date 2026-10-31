package com.example.TTGDHNDuongAn.repository;

import java.sql.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.example.TTGDHNDuongAn.dto.StudentPlanDtos;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class StudentPlanRepository {
    public static final String PLAN_SELECT="SELECT lp.*,EXISTS(SELECT 1 FROM plan_evaluations pe WHERE pe.learning_plan_id=lp.id) has_evaluation," +
        "EXISTS(SELECT 1 FROM plan_evaluations pe WHERE pe.learning_plan_id=lp.id AND pe.sent_to_parent=1) sent_to_parent FROM learning_plans lp ";
    private final JdbcTemplate jdbc;

    public StudentPlanRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<StudentPlanDtos.Student> students(String search) {
        return jdbc.query("SELECT id,code,full_name,birth_date,admitted_on,status FROM student " +
                        "WHERE (?='' OR code LIKE CONCAT('%',?,'%') OR full_name LIKE CONCAT('%',?,'%')) " +
                        "ORDER BY code,id LIMIT 100",
                (rs, n) -> student(rs), search, search, search);
    }

    public Optional<StudentPlanDtos.Student> student(String id) {
        return jdbc.query("SELECT id,code,full_name,birth_date,admitted_on,status FROM student WHERE id=?",
                (rs, n) -> student(rs), id).stream().findFirst();
    }

    public StudentPlanDtos.Student insertStudent(StudentPlanDtos.CreateStudent request) {
        String id = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO student(id,code,full_name,birth_date,admitted_on) VALUES(?,?,?,?,?)",
                id, request.code().trim(), request.fullName().trim(), Date.valueOf(request.birthDate()),
                Date.valueOf(request.admittedOn()));
        return student(id).orElseThrow();
    }

    public List<StudentPlanDtos.Plan> plans(String studentId) {
        return jdbc.query(PLAN_SELECT+"WHERE lp.student_id=? ORDER BY lp.start_date DESC,lp.id",
                (rs, n) -> plan(rs),
                studentId);
    }

    public StudentPlanDtos.Plan insertPlan(String studentId, StudentPlanDtos.CreatePlan request, String actorId) {
        String id = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO learning_plans(id,student_id,name,start_date,created_by) VALUES(?,?,?,?,?)",
                id, studentId, request.name().trim(), Date.valueOf(request.startDate()), actorId);
        return plans(studentId).stream().filter(p->p.id().equals(id)).findFirst().orElseThrow();
    }
    public static StudentPlanDtos.Plan plan(java.sql.ResultSet rs)throws java.sql.SQLException {
        return new StudentPlanDtos.Plan(rs.getString("id"),rs.getString("student_id"),rs.getString("name"),
            rs.getDate("start_date").toLocalDate(),rs.getString("status"),
            rs.getDate("end_date")==null?null:rs.getDate("end_date").toLocalDate(),
            rs.getTimestamp("created_at").toLocalDateTime(),rs.getBoolean("has_evaluation"),rs.getBoolean("sent_to_parent"));
    }

    private StudentPlanDtos.Student student(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new StudentPlanDtos.Student(rs.getString("id"), rs.getString("code"),
                rs.getString("full_name"), rs.getDate("birth_date").toLocalDate(),
                rs.getDate("admitted_on").toLocalDate(), rs.getString("status"));
    }
}

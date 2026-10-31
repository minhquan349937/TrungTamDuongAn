package com.example.TTGDHNDuongAn.service;

import com.example.TTGDHNDuongAn.dto.PlanEvaluationDtos.*;
import com.example.TTGDHNDuongAn.model.plan.*;
import com.example.TTGDHNDuongAn.repository.PlanEvaluationJpaRepository;
import com.example.TTGDHNDuongAn.security.AppUserPrincipal;
import jakarta.persistence.EntityManager;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PlanEvaluationService {
    private final JdbcTemplate jdbc;
    private final PlanEvaluationJpaRepository repository;
    private final EntityManager entities;
    private final PlanAccessService access;
    public PlanEvaluationService(JdbcTemplate jdbc,PlanEvaluationJpaRepository repository,EntityManager entities,PlanAccessService access) {
        this.jdbc=jdbc;this.repository=repository;this.entities=entities;this.access=access;
    }
    private static LocalDateTime now(){return LocalDateTime.now(Clock.systemUTC());}
    private static void uuid(String id) {
        try { if(!UUID.fromString(id).toString().equalsIgnoreCase(id)) throw new IllegalArgumentException(); }
        catch(IllegalArgumentException|NullPointerException ex) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Mã không hợp lệ."); }
    }
    private static String notes(Input input) {
        if(input==null || input.evaluationNotes()==null || input.evaluationNotes().isBlank() ||
           input.evaluationNotes().getBytes(StandardCharsets.UTF_8).length>65535)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Nhận xét phải có nội dung và không vượt quá giới hạn TEXT.");
        return input.evaluationNotes().trim();
    }
    private String planStudent(String planId) {
        uuid(planId);
        return jdbc.query("SELECT student_id FROM learning_plans WHERE id=?",rs->rs.next()?rs.getString(1):null,planId);
    }
    private void readAccess(String student,AppUserPrincipal actor) {
        if(student==null) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Không tìm thấy kế hoạch.");
        if("PARENT".equals(actor.role())) {
            Integer permitted=jdbc.queryForObject("SELECT COUNT(*) FROM student_guardian sg JOIN guardian g ON g.id=sg.guardian_id " +
                "JOIN app_user u ON u.id=g.user_id WHERE sg.student_id=? AND g.user_id=? AND sg.revoked_at IS NULL " +
                "AND sg.can_view_portal=1 AND u.role='PARENT' AND u.status='ACTIVE'",Integer.class,student,actor.id());
            if(permitted==null || permitted==0) throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Không có quyền xem báo cáo của học sinh này.");
        } else access.requireStudent(student,actor);
    }
    private void writeAccess(String student,AppUserPrincipal actor) {
        if(!"ADMIN".equals(actor.role()) && !"TEACHER".equals(actor.role()))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Chỉ giáo viên phụ trách được viết nhận xét.");
        readAccess(student,actor);
    }
    private void authorAccess(PlanEvaluation row,AppUserPrincipal actor) {
        if(!"ADMIN".equals(actor.role()) && !row.getEvaluatedBy().getId().equals(actor.id()))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Chỉ người tạo được sửa hoặc công bố bản nháp.");
    }
    private PlanEvaluation locked(String id) {
        uuid(id);
        return repository.lockById(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Không tìm thấy nhận xét."));
    }
    private Report report(String id) {
        return jdbc.query("SELECT e.*,p.name plan_name,p.student_id,s.full_name student_name,u.display_name actor_name " +
            "FROM plan_evaluations e JOIN learning_plans p ON p.id=e.learning_plan_id " +
            "JOIN student s ON s.id=p.student_id JOIN app_user u ON u.id=e.evaluated_by WHERE e.id=?",
            rs->rs.next()?map(rs):null,id);
    }
    private Report map(java.sql.ResultSet rs)throws java.sql.SQLException {
        return new Report(rs.getString("id"),rs.getString("learning_plan_id"),rs.getString("plan_name"),
            rs.getString("student_id"),rs.getString("student_name"),rs.getString("evaluation_notes"),
            rs.getString("evaluated_by"),rs.getString("actor_name"),rs.getTimestamp("evaluated_at").toLocalDateTime(),
            rs.getBoolean("sent_to_parent"),rs.getTimestamp("sent_at")==null?null:rs.getTimestamp("sent_at").toLocalDateTime(),
            rs.getTimestamp("updated_at").toLocalDateTime());
    }
    @Transactional(readOnly=true)
    public List<Report> list(String planId,AppUserPrincipal actor) {
        readAccess(planStudent(planId),actor);
        return jdbc.query("SELECT e.*,p.name plan_name,p.student_id,s.full_name student_name,u.display_name actor_name " +
            "FROM plan_evaluations e JOIN learning_plans p ON p.id=e.learning_plan_id " +
            "JOIN student s ON s.id=p.student_id JOIN app_user u ON u.id=e.evaluated_by " +
            "WHERE e.learning_plan_id=? AND (?=0 OR e.sent_to_parent=1) ORDER BY e.evaluated_at DESC,e.id DESC",
            (rs,n)->map(rs),planId,"PARENT".equals(actor.role())?1:0);
    }
    @Transactional(readOnly=true)
    public Report get(String id,AppUserPrincipal actor) {
        uuid(id);Report row=report(id);
        if(row==null) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Không tìm thấy nhận xét.");
        readAccess(row.studentId(),actor);
        if("PARENT".equals(actor.role()) && !row.sentToParent())
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Không tìm thấy báo cáo đã công bố.");
        return row;
    }
    @Transactional
    public Report create(String planId,Input input,AppUserPrincipal actor) {
        String text=notes(input);String student=planStudent(planId);writeAccess(student,actor);
        PlanEvaluation row=repository.saveAndFlush(new PlanEvaluation(entities.getReference(LearningPlanEntity.class,planId),
            entities.getReference(AppUserEntity.class,actor.id()),text,now()));
        return report(row.getId());
    }
    @Transactional
    public Report update(String id,Input input,AppUserPrincipal actor) {
        String text=notes(input);PlanEvaluation row=locked(id);
        writeAccess(planStudent(row.getLearningPlan().getId()),actor);authorAccess(row,actor);
        if(row.isSentToParent()) throw new ResponseStatusException(HttpStatus.CONFLICT,"Báo cáo đã công bố được giữ nguyên; hãy tạo nhận xét mới.");
        row.revise(text,now());repository.flush();return report(id);
    }
    @Transactional
    public Report publish(String id,AppUserPrincipal actor) {
        PlanEvaluation row=locked(id);
        String student=planStudent(row.getLearningPlan().getId());writeAccess(student,actor);authorAccess(row,actor);
        if(row.isSentToParent()) throw new ResponseStatusException(HttpStatus.CONFLICT,"Báo cáo đã được công bố.");
        Integer recipients=jdbc.queryForObject("SELECT COUNT(*) FROM student_guardian sg JOIN guardian g ON g.id=sg.guardian_id " +
            "JOIN app_user u ON u.id=g.user_id WHERE sg.student_id=? AND sg.revoked_at IS NULL " +
            "AND sg.can_view_portal=1 AND u.role='PARENT' AND u.status='ACTIVE'",Integer.class,student);
        if(recipients==null || recipients==0) throw new ResponseStatusException(HttpStatus.CONFLICT,
            "Chưa có tài khoản phụ huynh đang hoạt động được quyền xem báo cáo.");
        row.publish(now());repository.flush();return report(id);
    }
    @Transactional(readOnly=true)
    public List<Report> parentReports(AppUserPrincipal actor) {
        if(!"PARENT".equals(actor.role())) throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Chỉ phụ huynh được xem mục này.");
        return jdbc.query("SELECT e.*,p.name plan_name,p.student_id,s.full_name student_name,u.display_name actor_name " +
            "FROM plan_evaluations e JOIN learning_plans p ON p.id=e.learning_plan_id JOIN student s ON s.id=p.student_id " +
            "JOIN app_user u ON u.id=e.evaluated_by JOIN student_guardian sg ON sg.student_id=p.student_id " +
            "JOIN guardian g ON g.id=sg.guardian_id JOIN app_user parent ON parent.id=g.user_id " +
            "WHERE g.user_id=? AND parent.role='PARENT' AND parent.status='ACTIVE' AND sg.revoked_at IS NULL " +
            "AND sg.can_view_portal=1 AND e.sent_to_parent=1 ORDER BY e.sent_at DESC,e.id DESC",
            (rs,n)->map(rs),actor.id());
    }
}

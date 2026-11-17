package com.example.TTGDHNDuongAn.service;

import com.example.TTGDHNDuongAn.dto.IndividualReportDtos.*;
import com.example.TTGDHNDuongAn.dto.PlanEvaluationDtos;
import com.example.TTGDHNDuongAn.repository.*;
import com.example.TTGDHNDuongAn.security.AppUserPrincipal;
import java.time.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

@Service
public class IndividualReportService {
    private final JdbcTemplate jdbc;
    private final PlanAccessService access;
    private final PlanEvaluationService evaluations;
    private final PlanEvaluationJpaRepository repository;
    private final ReportSectionParser parser;
    private final ObjectMapper json=new ObjectMapper();
    public IndividualReportService(JdbcTemplate jdbc,PlanAccessService access,PlanEvaluationService evaluations,
            PlanEvaluationJpaRepository repository,ReportSectionParser parser) {
        this.jdbc=jdbc;this.access=access;this.evaluations=evaluations;this.repository=repository;this.parser=parser;
    }
    private void role(AppUserPrincipal actor) {
        if(!Set.of("ADMIN","TEACHER").contains(actor.role())) throw error(HttpStatus.FORBIDDEN,"Chỉ giáo viên phụ trách hoặc quản trị viên được xem báo cáo.");
    }
    private void id(String id) {
        if(id==null || !ShortIds.isId(id)) throw error(HttpStatus.BAD_REQUEST,"Mã không hợp lệ.");
    }
    private void student(String id,AppUserPrincipal actor) {
        role(actor);id(id);access.requireStudent(id,actor);
        if(!Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM student WHERE id=?)",Boolean.class,id)))
            throw error(HttpStatus.NOT_FOUND,"Không tìm thấy học sinh.");
    }
    @Transactional(readOnly=true)
    public List<Student> students(String search,AppUserPrincipal actor) {
        role(actor);
        String term=search==null?"":search.strip();
        if(term.length()>200) throw error(HttpStatus.BAD_REQUEST,"Từ khóa tìm kiếm quá dài.");
        // SQL filters access before returning any student/count information; no per-student N+1 access checks.
        return jdbc.query("""
            SELECT s.id,s.code,s.full_name,COUNT(e.id) report_count
            FROM student s JOIN learning_plans p ON p.student_id=s.id
            JOIN plan_evaluations e ON e.learning_plan_id=p.id
            WHERE (LOCATE(LOWER(?),LOWER(s.full_name))>0 OR LOCATE(LOWER(?),LOWER(s.code))>0)
            AND (?='ADMIN' OR EXISTS (
                SELECT 1 FROM enrollment en JOIN learning_class c ON c.id=en.class_id
                JOIN staff t ON t.id=c.teacher_id JOIN app_user u ON u.id=t.user_id
                WHERE en.student_id=s.id AND t.user_id=? AND u.status='ACTIVE' AND t.status='ACTIVE'
                AND c.status='ACTIVE' AND en.status='ENROLLED'
                AND en.started_on<=DATE(UTC_TIMESTAMP()+INTERVAL 7 HOUR)
                AND (en.ended_on IS NULL OR en.ended_on>DATE(UTC_TIMESTAMP()+INTERVAL 7 HOUR))
                AND c.starts_on<=DATE(UTC_TIMESTAMP()+INTERVAL 7 HOUR)
                AND (c.ends_on IS NULL OR c.ends_on>DATE(UTC_TIMESTAMP()+INTERVAL 7 HOUR))))
            GROUP BY s.id,s.code,s.full_name ORDER BY s.full_name,s.id
            """,(rs,n)->new Student(rs.getString("id"),rs.getString("code"),rs.getString("full_name"),rs.getLong("report_count")),
            term,term,actor.role(),actor.id());
    }
    @Transactional(readOnly=true)
    public Student studentInfo(String studentId,AppUserPrincipal actor) {
        student(studentId,actor);
        return jdbc.query("SELECT s.id,s.code,s.full_name,(SELECT COUNT(*) FROM plan_evaluations e JOIN learning_plans p ON p.id=e.learning_plan_id WHERE p.student_id=s.id) report_count FROM student s WHERE s.id=?",
            rs->{rs.next();return new Student(rs.getString("id"),rs.getString("code"),rs.getString("full_name"),rs.getLong("report_count"));},studentId);
    }
    @Transactional(readOnly=true)
    public List<Summary> reports(String studentId,AppUserPrincipal actor) {
        student(studentId,actor);
        return jdbc.query("""
            SELECT e.id,e.evaluated_by,e.sent_to_parent,e.evaluated_at,p.id plan_id,p.name,p.start_date,p.end_date,u.display_name
            FROM plan_evaluations e JOIN learning_plans p ON p.id=e.learning_plan_id JOIN app_user u ON u.id=e.evaluated_by
            WHERE p.student_id=? ORDER BY e.evaluated_at DESC,e.id DESC
            """,(rs,n)->new Summary(rs.getString("id"),rs.getString("plan_id"),rs.getString("name"),rs.getDate("start_date").toLocalDate(),
                rs.getDate("end_date")==null?null:rs.getDate("end_date").toLocalDate(),rs.getTimestamp("evaluated_at").toLocalDateTime(),
                rs.getString("display_name"),!rs.getBoolean("sent_to_parent") && (actor.role().equals("ADMIN") || actor.id().equals(rs.getString("evaluated_by")))),studentId);
    }
    @Transactional(readOnly=true)
    public Detail detail(String reportId,AppUserPrincipal actor) {
        role(actor);id(reportId);
        var report=evaluations.get(reportId,actor);
        var meta=jdbc.queryForMap("SELECT p.start_date,p.end_date,s.code FROM learning_plans p JOIN student s ON s.id=p.student_id WHERE p.id=?",report.learningPlanId());
        LocalDate start=((java.sql.Date)meta.get("start_date")).toLocalDate();
        LocalDate end=meta.get("end_date")==null?null:((java.sql.Date)meta.get("end_date")).toLocalDate();
        LocalDateTime cutoff=report.evaluatedAt();
        if(end!=null) {
            LocalDateTime endUtc=end.plusDays(1).atStartOfDay().minusNanos(1000).minusHours(7);
            if(endUtc.isBefore(cutoff)) cutoff=endUtc;
        }
        var warnings=new ArrayList<String>();
        LocalDateTime asOf=cutoff;
        var goals=jdbc.query("""
            SELECT pg.id,pg.goal_snapshot,e.id evaluation_id,e.achievement_percent,e.result_status,e.evaluation_notes,
                e.updated_at,e.evaluated_at
            FROM plan_goals pg
            LEFT JOIN goal_evaluations e ON e.plan_goal_id=pg.id AND e.attempt_no=(
                SELECT MAX(previous.attempt_no) FROM goal_evaluations previous WHERE previous.plan_goal_id=pg.id AND previous.created_at<=?)
            WHERE pg.plan_id=? AND pg.assigned_at<=? AND NOT EXISTS (
                SELECT 1 FROM plan_goals successor WHERE successor.supersedes_id=pg.id AND successor.plan_id=pg.plan_id AND successor.assigned_at<=?)
            ORDER BY pg.assigned_at,pg.id
            """,(rs,n)->{
                var snapshot=json.readTree(rs.getString("goal_snapshot"));
                boolean revised=rs.getString("evaluation_id")!=null && (rs.getTimestamp("updated_at").toLocalDateTime().isAfter(asOf) ||
                    rs.getTimestamp("evaluated_at")!=null && rs.getTimestamp("evaluated_at").toLocalDateTime().isAfter(asOf));
                return new Goal(rs.getString("id"),snapshot.path("domain_name").asText("Chưa xác định lĩnh vực"),
                    snapshot.path("code").asText(""),snapshot.path("name").asText("Mục tiêu thiếu nội dung"),
                    revised?null:rs.getBigDecimal("achievement_percent"),revised?"HISTORICAL_UNAVAILABLE":
                    rs.getString("evaluation_id")==null?"NOT_STARTED":rs.getString("result_status"),revised?null:rs.getString("evaluation_notes"));
            },asOf,report.learningPlanId(),asOf,asOf);
        if(goals.stream().anyMatch(g->g.status().equals("HISTORICAL_UNAVAILABLE"))) warnings.add("Một số đánh giá đã được sửa sau kỳ báo cáo. Không còn phiên bản cũ để xác định kết quả tại thời điểm đó; kết quả mới không được đưa vào báo cáo này.");
        if(goals.isEmpty()) warnings.add("Không có mục tiêu được ghi nhận trong kế hoạch tại thời điểm báo cáo.");
        return new Detail(report,(String)meta.get("code"),start,end,asOf,!report.sentToParent() &&
            (actor.role().equals("ADMIN") || report.evaluatedBy().equals(actor.id())),parser.parse(report.evaluationNotes()),goals,warnings);
    }
    @Transactional
    public Detail update(String reportId,Edit edit,AppUserPrincipal actor) {
        role(actor);id(reportId);
        // The existing service enforces assignment, original author and publication lock again at save.
        detail(reportId,actor);
        repository.lockById(reportId).orElseThrow(()->error(HttpStatus.NOT_FOUND,"Không tìm thấy báo cáo."));
        if(edit==null || edit.expectedUpdatedAt()==null) throw error(HttpStatus.BAD_REQUEST,"Thiếu phiên bản báo cáo đang chỉnh sửa.");
        // Compare the persisted microsecond value, not an entity's unrounded Java nanoseconds after a prior flush.
        LocalDateTime savedVersion=jdbc.queryForObject("SELECT updated_at FROM plan_evaluations WHERE id=? FOR UPDATE",(rs,n)->rs.getTimestamp(1).toLocalDateTime(),reportId);
        if(!edit.expectedUpdatedAt().equals(savedVersion)) throw error(HttpStatus.CONFLICT,"Báo cáo đã được thay đổi. Hãy tải lại và đối chiếu nội dung trước khi lưu.");
        String notes;
        try { notes=parser.compose(edit.strengths(),edit.difficulties(),edit.supportDirections()); }
        catch(IllegalArgumentException ex) { throw error(HttpStatus.BAD_REQUEST,ex.getMessage()); }
        evaluations.update(reportId,new PlanEvaluationDtos.Input(notes),actor);
        return detail(reportId,actor);
    }
    private ResponseStatusException error(HttpStatus status,String reason) { return new ResponseStatusException(status,reason); }
}

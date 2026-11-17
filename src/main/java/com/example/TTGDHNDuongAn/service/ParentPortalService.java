package com.example.TTGDHNDuongAn.service;

import com.example.TTGDHNDuongAn.security.AppUserPrincipal;
import java.time.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional(readOnly = true)
public class ParentPortalService {
    public record Guardian(String id,String fullName,String phone,String email) {}
    public record Child(String id,String code,String fullName,LocalDate birthDate,LocalDate admittedOn,String status) {}
    public record Membership(String classId,String code,String name,String type,String teacherId,int capacity,String status,String studentId,LocalDate startedOn,LocalDate endedOn) {}
    public record Teacher(String id,String fullName) {}
    public record Session(String id,String classId,String teacherId,LocalDateTime startsAt,LocalDateTime endsAt,String location) {}
    public record Attendance(String sessionId,String studentId,String status) {}
    public record Notice(String id,String title,String body,LocalDateTime publishedAt) {}
    public record Portal(String linkStatus,Guardian guardian,List<Child> children,List<Membership> memberships,
                         List<Teacher> teachers,List<Session> sessions,List<Attendance> attendance,List<Notice> notices) {}
    private final JdbcTemplate jdbc;
    public ParentPortalService(JdbcTemplate jdbc) { this.jdbc=jdbc; }

    // Basic family information is visible through an active family link. Do not change
    // can_view_portal or return evaluations/internal student notes through this endpoint.
    private static final String FAMILY = """
        EXISTS (SELECT 1 FROM student_guardian sg JOIN guardian g ON g.id=sg.guardian_id
        WHERE sg.student_id=s.id AND sg.revoked_at IS NULL AND g.user_id=?)
        """;
    private static final String CURRENT = """
        e.status='ENROLLED' AND e.started_on<=DATE(UTC_TIMESTAMP()+INTERVAL 7 HOUR)
        AND (e.ended_on IS NULL OR e.ended_on>DATE(UTC_TIMESTAMP()+INTERVAL 7 HOUR))
        AND c.status IN ('ACTIVE','PAUSED') AND c.starts_on<=DATE(UTC_TIMESTAMP()+INTERVAL 7 HOUR)
        AND (c.ends_on IS NULL OR c.ends_on>DATE(UTC_TIMESTAMP()+INTERVAL 7 HOUR))
        """;
    private static final String MEMBER = """
        SELECT e.class_id FROM enrollment e JOIN student s ON s.id=e.student_id
        JOIN learning_class c ON c.id=e.class_id WHERE
        """ + CURRENT + " AND " + FAMILY;
    private static final String OWN_SESSION = """
        SELECT cs.id FROM class_session cs JOIN learning_class c ON c.id=cs.class_id
        WHERE cs.status<>'CANCELLED' AND EXISTS (
          SELECT 1 FROM enrollment e JOIN student s ON s.id=e.student_id
          WHERE e.class_id=cs.class_id AND
        """ + CURRENT + " AND " + FAMILY + """
          AND DATE(cs.starts_at+INTERVAL 7 HOUR)>=e.started_on
          AND (e.ended_on IS NULL OR DATE(cs.starts_at+INTERVAL 7 HOUR)<e.ended_on)
          AND DATE(cs.starts_at+INTERVAL 7 HOUR)>=c.starts_on
          AND (c.ends_on IS NULL OR DATE(cs.starts_at+INTERVAL 7 HOUR)<c.ends_on))
        """;

    public Portal get(AppUserPrincipal actor) {
        if (actor == null || !"PARENT".equals(actor.role())) throw denied();
        Integer active=jdbc.queryForObject("SELECT COUNT(*) FROM app_user WHERE id=? AND role='PARENT' AND status='ACTIVE'",Integer.class,actor.id());
        if (active == null || active != 1) throw denied();
        var guardians=jdbc.query("SELECT id,full_name,phone,email FROM guardian WHERE user_id=?",
            (rs,n)->new Guardian(rs.getString("id"),rs.getString("full_name"),rs.getString("phone"),rs.getString("email")),actor.id());
        if (guardians.isEmpty()) return empty("NO_GUARDIAN_PROFILE",null);
        Guardian guardian=guardians.getFirst();
        var children=jdbc.query("SELECT s.id,s.code,s.full_name,s.birth_date,s.admitted_on,s.status FROM student s WHERE " + FAMILY + " ORDER BY s.full_name,s.id",
            (rs,n)->new Child(rs.getString("id"),rs.getString("code"),rs.getString("full_name"),rs.getDate("birth_date").toLocalDate(),
                rs.getDate("admitted_on").toLocalDate(),rs.getString("status")),actor.id());
        if (children.isEmpty()) return empty("NO_LINKED_STUDENT",guardian);
        var memberships=jdbc.query("""
            SELECT DISTINCT c.id,c.code,c.name,c.class_type,c.teacher_id,c.capacity,c.status,e.student_id,e.started_on,e.ended_on
            FROM enrollment e JOIN learning_class c ON c.id=e.class_id JOIN student s ON s.id=e.student_id
            WHERE
            """ + CURRENT + " AND " + FAMILY + " ORDER BY c.id,e.student_id",
            (rs,n)->new Membership(rs.getString("id"),rs.getString("code"),rs.getString("name"),rs.getString("class_type"),
                rs.getString("teacher_id"),rs.getInt("capacity"),rs.getString("status"),rs.getString("student_id"),rs.getDate("started_on").toLocalDate(),
                rs.getDate("ended_on")==null?null:rs.getDate("ended_on").toLocalDate()),actor.id());
        var sessions=jdbc.query("SELECT cs.id,cs.class_id,cs.teacher_id,cs.starts_at,cs.ends_at,cs.location_note FROM class_session cs WHERE cs.id IN ("+OWN_SESSION+") ORDER BY cs.starts_at,cs.id",
            (rs,n)->new Session(rs.getString("id"),rs.getString("class_id"),rs.getString("teacher_id"),rs.getTimestamp("starts_at").toLocalDateTime(),
                rs.getTimestamp("ends_at").toLocalDateTime(),rs.getString("location_note")),actor.id());
        var teachers=jdbc.query("SELECT t.id,t.full_name FROM staff t WHERE t.id IN (SELECT c.teacher_id FROM learning_class c WHERE c.id IN ("+MEMBER+
            ")) OR t.id IN (SELECT cs.teacher_id FROM class_session cs WHERE cs.id IN ("+OWN_SESSION+")) ORDER BY t.full_name,t.id",
            (rs,n)->new Teacher(rs.getString("id"),rs.getString("full_name")),actor.id(),actor.id());
        var attendance=jdbc.query("SELECT a.session_id,a.student_id,a.status FROM student_attendance a JOIN student s ON s.id=a.student_id WHERE "+FAMILY+
            " AND a.session_id IN ("+OWN_SESSION+") AND EXISTS (SELECT 1 FROM enrollment e JOIN class_session cs ON cs.class_id=e.class_id JOIN learning_class c ON c.id=e.class_id WHERE cs.id=a.session_id AND e.student_id=a.student_id AND "+CURRENT+
            " AND DATE(cs.starts_at+INTERVAL 7 HOUR)>=e.started_on AND (e.ended_on IS NULL OR DATE(cs.starts_at+INTERVAL 7 HOUR)<e.ended_on))",
            (rs,n)->new Attendance(rs.getString("session_id"),rs.getString("student_id"),rs.getString("status")),actor.id(),actor.id());
        var notices=jdbc.query("""
            SELECT n.id,n.title,n.body,n.published_at FROM notification n
            WHERE n.status='PUBLISHED' AND n.published_at<=UTC_TIMESTAMP(6)
            AND EXISTS (SELECT 1 FROM notification_recipient r WHERE r.notification_id=n.id AND r.user_id=?)
            ORDER BY n.published_at DESC,n.id DESC
            """,(rs,n)->new Notice(rs.getString("id"),rs.getString("title"),rs.getString("body"),rs.getTimestamp("published_at").toLocalDateTime()),actor.id());
        return new Portal("READY",guardian,children,memberships,teachers,sessions,attendance,notices);
    }
    private Portal empty(String status,Guardian guardian) { return new Portal(status,guardian,List.of(),List.of(),List.of(),List.of(),List.of(),List.of()); }
    private ResponseStatusException denied() { return new ResponseStatusException(HttpStatus.FORBIDDEN,"Tài khoản không có quyền truy cập hồ sơ phụ huynh."); }
}

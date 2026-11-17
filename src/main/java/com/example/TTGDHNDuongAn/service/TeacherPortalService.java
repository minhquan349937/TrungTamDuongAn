package com.example.TTGDHNDuongAn.service;

import com.example.TTGDHNDuongAn.security.AppUserPrincipal;
import com.example.TTGDHNDuongAn.service.ParentPortalService.*;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional(readOnly=true)
public class TeacherPortalService {
    public record Profile(String id,String fullName) {}
    public record AssignedClass(String id,String code,String name,String type,String teacherId,int capacity,String status) {}
    public record Portal(String linkStatus,Profile teacher,List<Child> students,List<AssignedClass> classes,
        List<Membership> memberships,List<Session> sessions,List<Attendance> attendance,List<Notice> notices) {}
    private final JdbcTemplate jdbc;
    public TeacherPortalService(JdbcTemplate jdbc) {this.jdbc=jdbc;}
    private static final String TODAY="DATE(UTC_TIMESTAMP()+INTERVAL 7 HOUR)";
    private static final String OWN_CLASS="c.teacher_id=? AND c.status='ACTIVE' AND c.starts_on<="+TODAY+
        " AND (c.ends_on IS NULL OR c.ends_on>"+TODAY+")";
    private static final String ENROLLED="e.status='ENROLLED' AND e.started_on<="+TODAY+
        " AND (e.ended_on IS NULL OR e.ended_on>"+TODAY+")";
    public Portal get(AppUserPrincipal actor) {
        if(actor==null || !"TEACHER".equals(actor.role()) || !Boolean.TRUE.equals(jdbc.queryForObject(
            "SELECT EXISTS(SELECT 1 FROM app_user WHERE id=? AND role='TEACHER' AND status='ACTIVE')",Boolean.class,actor.id())))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Tài khoản không có quyền truy cập trang giáo viên.");
        var profiles=jdbc.query("SELECT id,full_name FROM staff WHERE user_id=? AND status='ACTIVE'",
            (rs,n)->new Profile(rs.getString("id"),rs.getString("full_name")),actor.id());
        if(profiles.isEmpty()) return new Portal("NO_ACTIVE_STAFF_PROFILE",null,List.of(),List.of(),List.of(),List.of(),List.of(),List.of());
        Profile teacher=profiles.getFirst();String staffId=teacher.id();
        var classes=jdbc.query("SELECT c.id,c.code,c.name,c.class_type,c.teacher_id,c.capacity,c.status FROM learning_class c WHERE "+OWN_CLASS+" ORDER BY c.name,c.id",
            (rs,n)->new AssignedClass(rs.getString("id"),rs.getString("code"),rs.getString("name"),rs.getString("class_type"),rs.getString("teacher_id"),rs.getInt("capacity"),rs.getString("status")),staffId);
        var students=jdbc.query("SELECT DISTINCT s.id,s.code,s.full_name,s.birth_date,s.admitted_on,s.status FROM student s JOIN enrollment e ON e.student_id=s.id JOIN learning_class c ON c.id=e.class_id WHERE "+OWN_CLASS+" AND "+ENROLLED+" ORDER BY s.full_name,s.id",
            (rs,n)->new Child(rs.getString("id"),rs.getString("code"),rs.getString("full_name"),rs.getDate("birth_date").toLocalDate(),rs.getDate("admitted_on").toLocalDate(),rs.getString("status")),staffId);
        var memberships=jdbc.query("SELECT c.id,c.code,c.name,c.class_type,c.teacher_id,c.capacity,c.status,e.student_id,e.started_on,e.ended_on FROM enrollment e JOIN learning_class c ON c.id=e.class_id WHERE "+OWN_CLASS+" AND "+ENROLLED+" ORDER BY c.id,e.student_id",
            (rs,n)->new Membership(rs.getString("id"),rs.getString("code"),rs.getString("name"),rs.getString("class_type"),rs.getString("teacher_id"),rs.getInt("capacity"),rs.getString("status"),rs.getString("student_id"),rs.getDate("started_on").toLocalDate(),rs.getDate("ended_on")==null?null:rs.getDate("ended_on").toLocalDate()),staffId);
        var sessions=jdbc.query("SELECT cs.id,cs.class_id,cs.teacher_id,cs.starts_at,cs.ends_at,cs.location_note FROM class_session cs JOIN learning_class c ON c.id=cs.class_id WHERE "+OWN_CLASS+" AND cs.teacher_id=c.teacher_id AND cs.status<>'CANCELLED' ORDER BY cs.starts_at,cs.id",
            (rs,n)->new Session(rs.getString("id"),rs.getString("class_id"),rs.getString("teacher_id"),rs.getTimestamp("starts_at").toLocalDateTime(),rs.getTimestamp("ends_at").toLocalDateTime(),rs.getString("location_note")),staffId);
        var attendance=jdbc.query("SELECT a.session_id,a.student_id,a.status FROM student_attendance a JOIN class_session cs ON cs.id=a.session_id JOIN learning_class c ON c.id=cs.class_id WHERE "+OWN_CLASS+" AND cs.teacher_id=c.teacher_id AND cs.status<>'CANCELLED' AND EXISTS(SELECT 1 FROM enrollment e WHERE e.class_id=c.id AND e.student_id=a.student_id AND "+ENROLLED+" AND DATE(cs.starts_at+INTERVAL 7 HOUR)>=e.started_on AND (e.ended_on IS NULL OR DATE(cs.starts_at+INTERVAL 7 HOUR)<e.ended_on))",
            (rs,n)->new Attendance(rs.getString("session_id"),rs.getString("student_id"),rs.getString("status")),staffId);
        var notices=jdbc.query("SELECT n.id,n.title,n.body,n.published_at FROM notification n WHERE n.status='PUBLISHED' AND n.published_at<=UTC_TIMESTAMP(6) AND EXISTS(SELECT 1 FROM notification_recipient r WHERE r.notification_id=n.id AND r.user_id=?) ORDER BY n.published_at DESC,n.id DESC",
            (rs,n)->new Notice(rs.getString("id"),rs.getString("title"),rs.getString("body"),rs.getTimestamp("published_at").toLocalDateTime()),actor.id());
        return new Portal("READY",teacher,students,classes,memberships,sessions,attendance,notices);
    }
}

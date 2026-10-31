package com.example.TTGDHNDuongAn.service;

import java.sql.Date;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class IndividualAssignmentService {
    public record Teacher(String id,String code,String fullName) {}
    public record Assignment(String id,String code,String studentId,String studentCode,String studentName,
        String teacherId,String teacherName,LocalDate startsOn) {}
    public record Create(String studentId,String teacherId) {}
    public record Change(String teacherId) {}
    private final JdbcTemplate jdbc;
    public IndividualAssignmentService(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    public List<Teacher> teachers() {
        return jdbc.query("SELECT t.id,t.code,t.full_name FROM staff t JOIN app_user u ON u.id=t.user_id " +
            "WHERE t.status='ACTIVE' AND u.status='ACTIVE' AND u.role='TEACHER' ORDER BY t.full_name,t.id",
            (rs,n)->new Teacher(rs.getString("id"),rs.getString("code"),rs.getString("full_name")));
    }
    public List<Assignment> list() {
        return jdbc.query("SELECT c.id,c.code,e.student_id,s.code student_code,s.full_name student_name,t.id teacher_id,t.full_name teacher_name,c.starts_on " +
            "FROM learning_class c JOIN enrollment e ON e.class_id=c.id JOIN student s ON s.id=e.student_id JOIN staff t ON t.id=c.teacher_id " +
            "WHERE c.class_type='INDIVIDUAL' AND c.status='ACTIVE' AND e.status='ENROLLED' " +
            "AND e.started_on<=DATE(UTC_TIMESTAMP()+INTERVAL 7 HOUR) AND (e.ended_on IS NULL OR e.ended_on>DATE(UTC_TIMESTAMP()+INTERVAL 7 HOUR)) " +
            "AND c.starts_on<=DATE(UTC_TIMESTAMP()+INTERVAL 7 HOUR) AND (c.ends_on IS NULL OR c.ends_on>DATE(UTC_TIMESTAMP()+INTERVAL 7 HOUR)) " +
            "ORDER BY s.full_name,c.id",(rs,n)->new Assignment(rs.getString("id"),rs.getString("code"),rs.getString("student_id"),
                rs.getString("student_code"),rs.getString("student_name"),rs.getString("teacher_id"),rs.getString("teacher_name"),rs.getDate("starts_on").toLocalDate()));
    }
    @Transactional public Assignment create(Create input) {
        if(input==null) throw invalid();
        uuid(input.studentId());uuid(input.teacherId());
        var students=jdbc.queryForList("SELECT full_name FROM student WHERE id=? AND status='ACTIVE' FOR UPDATE",String.class,input.studentId());
        if(students.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Học sinh phải đang theo học.");
        requireTeacher(input.teacherId());
        if(list().stream().anyMatch(a->a.studentId().equals(input.studentId()) && a.teacherId().equals(input.teacherId())))
            throw new ResponseStatusException(HttpStatus.CONFLICT,"Giáo viên đã được phân công cho học sinh này.");
        String cls=UUID.randomUUID().toString();
        Date date=Date.valueOf(LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")));
        String name="Cá nhân · "+students.getFirst();
        jdbc.update("INSERT INTO learning_class(id,code,name,class_type,teacher_id,capacity,starts_on,status) VALUES(?,?,?,'INDIVIDUAL',?,1,?,'ACTIVE')",
            cls,"CN-"+cls.substring(0,8),name.substring(0,Math.min(150,name.length())),input.teacherId(),date);
        jdbc.update("INSERT INTO enrollment(id,class_id,student_id,started_on,status) VALUES(?,?,?,?,'ENROLLED')",
            UUID.randomUUID().toString(),cls,input.studentId(),date);
        return list().stream().filter(a->a.id().equals(cls)).findFirst().orElseThrow();
    }
    @Transactional public Assignment change(String id,Change input) {
        uuid(id);if(input==null) throw invalid();uuid(input.teacherId());
        if(jdbc.queryForList("SELECT id FROM learning_class WHERE id=? AND class_type='INDIVIDUAL' AND status='ACTIVE' FOR UPDATE",String.class,id).isEmpty())
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Không tìm thấy phân công cá nhân đang hoạt động.");
        requireTeacher(input.teacherId());
        var assignment=list().stream().filter(a->a.id().equals(id)).findFirst()
            .orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Không tìm thấy ghi danh đang hoạt động."));
        if(list().stream().anyMatch(a->!a.id().equals(id) && a.studentId().equals(assignment.studentId()) && a.teacherId().equals(input.teacherId())))
            throw new ResponseStatusException(HttpStatus.CONFLICT,"Giáo viên đã được phân công cho học sinh này.");
        jdbc.update("UPDATE learning_class SET teacher_id=? WHERE id=?",input.teacherId(),id);
        return list().stream().filter(a->a.id().equals(id)).findFirst().orElseThrow();
    }
    private void requireTeacher(String id) {
        if(jdbc.queryForList("SELECT t.id FROM staff t JOIN app_user u ON u.id=t.user_id WHERE t.id=? AND t.status='ACTIVE' AND u.status='ACTIVE' AND u.role='TEACHER' FOR UPDATE",String.class,id).isEmpty())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Chọn giáo viên đang làm việc có tài khoản giáo viên hoạt động.");
    }
    private void uuid(String id) {
        try { UUID.fromString(id); } catch(IllegalArgumentException|NullPointerException ex) { throw invalid(); }
    }
    private ResponseStatusException invalid() { return new ResponseStatusException(HttpStatus.BAD_REQUEST,"Thông tin phân công không hợp lệ."); }
}

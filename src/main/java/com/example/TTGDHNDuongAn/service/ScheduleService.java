package com.example.TTGDHNDuongAn.service;

import com.example.TTGDHNDuongAn.repository.ShortIds;
import com.example.TTGDHNDuongAn.security.AppUserPrincipal;
import java.time.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ScheduleService {
    public record Person(String id,String name) {}
    public record ClassChoice(String id,String name,String type,String teacherId,int capacity,String status,
        LocalDate startsOn,LocalDate endsOn,boolean teacherAvailable) {}
    public record Lesson(String id,String classId,String teacherId,LocalDate date,LocalTime start,LocalTime end,
        String room,List<String> studentIds,Map<String,String> attendance) {}
    public record Calendar(List<ClassChoice> classes,List<Person> staff,List<Person> students,List<Lesson> sessions) {}
    public record Input(String classId,LocalDate date,LocalTime start,LocalTime end,String room,String changeReason) {}
    public record AttendanceInput(Map<String,String> attendance) {}
    private record Enrollment(String classId,String studentId,LocalDate start,LocalDate end) {}
    private final JdbcTemplate jdbc;
    private final NamedParameterJdbcTemplate named;
    public ScheduleService(JdbcTemplate jdbc) {this.jdbc=jdbc;this.named=new NamedParameterJdbcTemplate(jdbc);}
    private static final ZoneOffset VN=ZoneOffset.ofHours(7);
    private static final String FAMILY="EXISTS(SELECT 1 FROM student_guardian sg JOIN guardian g ON g.id=sg.guardian_id WHERE sg.student_id=e.student_id AND sg.revoked_at IS NULL AND g.user_id=:actor)";
    private static final String OWN_TEACHER="IN (SELECT id FROM staff WHERE user_id=:actor AND status='ACTIVE')";
    private void active(AppUserPrincipal actor) {
        if(actor==null || !Set.of("ADMIN","TEACHER","PARENT").contains(actor.role()) || !Boolean.TRUE.equals(jdbc.queryForObject(
            "SELECT EXISTS(SELECT 1 FROM app_user WHERE id=? AND role=? AND status='ACTIVE')",Boolean.class,actor.id(),actor.role())))
            throw error(HttpStatus.FORBIDDEN,"Tài khoản không có quyền truy cập lịch học.");
    }
    private void admin(AppUserPrincipal actor) {active(actor);if(!actor.role().equals("ADMIN")) throw error(HttpStatus.FORBIDDEN,"Chỉ quản trị viên được điều phối lịch học.");}
    private LocalDateTime utc(LocalDate date,LocalTime time) {return date.atTime(time).atOffset(VN).withOffsetSameInstant(ZoneOffset.UTC).toLocalDateTime();}
    private LocalDateTime local(java.sql.ResultSet rs,String column) throws java.sql.SQLException {return rs.getObject(column,LocalDateTime.class).plusHours(7);}
    private String sessionScope(AppUserPrincipal actor) {
        if(actor.role().equals("ADMIN")) return "1=1";
        if(actor.role().equals("TEACHER")) return "cs.teacher_id "+OWN_TEACHER;
        return "EXISTS(SELECT 1 FROM enrollment e WHERE e.class_id=cs.class_id AND e.status<>'CANCELLED' AND DATE(cs.starts_at+INTERVAL 7 HOUR)>=e.started_on AND (e.ended_on IS NULL OR DATE(cs.starts_at+INTERVAL 7 HOUR)<e.ended_on) AND "+FAMILY+")";
    }
    @Transactional(readOnly=true)
    public Calendar get(LocalDate from,LocalDate to,AppUserPrincipal actor) {
        active(actor);
        if(from==null || to==null || to.isBefore(from) || to.isAfter(from.plusDays(62))) throw error(HttpStatus.BAD_REQUEST,"Khoảng xem lịch phải từ 1 đến 63 ngày.");
        var params=new HashMap<String,Object>();params.put("actor",actor.id());params.put("from",utc(from,LocalTime.MIDNIGHT));params.put("until",utc(to.plusDays(1),LocalTime.MIDNIGHT));params.put("first",from);params.put("last",to);
        String visibleSessions="SELECT cs.id FROM class_session cs WHERE cs.status<>'CANCELLED' AND cs.starts_at>=:from AND cs.starts_at<:until AND "+sessionScope(actor);
        String classScope=actor.role().equals("ADMIN")?"c.status<>'CLOSED'":actor.role().equals("TEACHER")?"c.teacher_id "+OWN_TEACHER:
            "EXISTS(SELECT 1 FROM enrollment e WHERE e.class_id=c.id AND e.status<>'CANCELLED' AND e.started_on<=:last AND (e.ended_on IS NULL OR e.ended_on>:first) AND "+FAMILY+")";
        var classes=named.query("SELECT c.*,EXISTS(SELECT 1 FROM staff t JOIN app_user u ON u.id=t.user_id WHERE t.id=c.teacher_id AND t.status='ACTIVE' AND u.status='ACTIVE' AND u.role IN ('ADMIN','TEACHER')) teacher_available FROM learning_class c WHERE ("+classScope+") OR c.id IN (SELECT cs.class_id FROM class_session cs WHERE cs.id IN ("+visibleSessions+")) ORDER BY c.name,c.id",
            params,(rs,n)->new ClassChoice(rs.getString("id"),rs.getString("name"),rs.getString("class_type"),rs.getString("teacher_id"),rs.getInt("capacity"),rs.getString("status"),rs.getObject("starts_on",LocalDate.class),rs.getObject("ends_on",LocalDate.class),rs.getBoolean("teacher_available")));
        var raw=named.query("SELECT cs.* FROM class_session cs WHERE cs.id IN ("+visibleSessions+") ORDER BY cs.starts_at,cs.id",params,
            (rs,n)->new Lesson(rs.getString("id"),rs.getString("class_id"),rs.getString("teacher_id"),local(rs,"starts_at").toLocalDate(),local(rs,"starts_at").toLocalTime(),local(rs,"ends_at").toLocalTime(),rs.getString("location_note"),List.of(),Map.of()));
        if(classes.isEmpty()) return new Calendar(List.of(),List.of(),List.of(),List.of());
        params.put("classes",classes.stream().map(ClassChoice::id).toList());
        var enrollments=named.query("SELECT e.class_id,e.student_id,e.started_on,e.ended_on FROM enrollment e WHERE e.class_id IN (:classes) AND e.status<>'CANCELLED' AND e.started_on<=:last AND (e.ended_on IS NULL OR e.ended_on>:first)"+(actor.role().equals("PARENT")?" AND "+FAMILY:""),params,
            (rs,n)->new Enrollment(rs.getString("class_id"),rs.getString("student_id"),rs.getObject("started_on",LocalDate.class),rs.getObject("ended_on",LocalDate.class)));
        var attendance=new HashMap<String,Map<String,String>>();
        if(!raw.isEmpty()) {
            params.put("sessions",raw.stream().map(Lesson::id).toList());
            named.query("SELECT session_id,student_id,status FROM student_attendance WHERE session_id IN (:sessions)",params,rs->{
                attendance.computeIfAbsent(rs.getString("session_id"),key->new HashMap<>()).put(rs.getString("student_id"),rs.getString("status"));
            });
        }
        var sessions=raw.stream().map(row->{
            var roster=enrollments.stream().filter(e->e.classId().equals(row.classId()) && !e.start().isAfter(row.date()) && (e.end()==null || e.end().isAfter(row.date()))).map(Enrollment::studentId).distinct().toList();
            var marks=new HashMap<String,String>();var recorded=attendance.getOrDefault(row.id(),Map.of());
            for(String student:roster) if(recorded.containsKey(student)) marks.put(student,recorded.get(student));
            return new Lesson(row.id(),row.classId(),row.teacherId(),row.date(),row.start(),row.end(),row.room(),roster,marks);
        }).toList();
        var studentIds=sessions.stream().flatMap(s->s.studentIds().stream()).distinct().toList();
        List<Person> students=List.of();
        if(!studentIds.isEmpty()) {params.put("students",studentIds);students=named.query("SELECT id,full_name FROM student WHERE id IN (:students) ORDER BY full_name,id",params,(rs,n)->new Person(rs.getString("id"),rs.getString("full_name")));}
        var teacherIds=new HashSet<String>();classes.forEach(c->teacherIds.add(c.teacherId()));sessions.forEach(s->teacherIds.add(s.teacherId()));params.put("teachers",teacherIds);
        var teachers=named.query("SELECT id,full_name FROM staff WHERE id IN (:teachers) ORDER BY full_name,id",params,(rs,n)->new Person(rs.getString("id"),rs.getString("full_name")));
        return new Calendar(classes,teachers,students,sessions);
    }
    private void lockSchedule() {
        // Serialize even an empty calendar without inventing a center profile.
        if(jdbc.queryForList("SELECT id FROM schedule_write_lock WHERE id=1 FOR UPDATE",Integer.class).isEmpty()) throw error(HttpStatus.CONFLICT,"Chưa khởi tạo cấu hình lưu lịch học.");
        // Also coordinate with the legacy procedure when a center profile exists.
        jdbc.queryForList("SELECT id FROM center WHERE id=1 FOR UPDATE",Integer.class);
    }
    private List<String> roster(String classId,LocalDate day) {
        return jdbc.queryForList("SELECT DISTINCT student_id FROM enrollment WHERE class_id=? AND status<>'CANCELLED' AND started_on<=? AND (ended_on IS NULL OR ended_on>?) ORDER BY student_id",String.class,classId,day,day);
    }
    @Transactional
    public String save(String id,Input input,AppUserPrincipal actor) {
        admin(actor);
        if(input==null || input.classId()==null || input.date()==null || input.start()==null || input.end()==null || !input.end().isAfter(input.start())) throw error(HttpStatus.BAD_REQUEST,"Vui lòng chọn lớp, ngày học và giờ kết thúc sau giờ bắt đầu.");
        if(input.room()==null || input.room().isBlank() || input.room().strip().length()>250) throw error(HttpStatus.BAD_REQUEST,"Phòng học phải có từ 1 đến 250 ký tự.");
        if(input.changeReason()!=null && input.changeReason().length()>4000) throw error(HttpStatus.BAD_REQUEST,"Lý do đổi lịch quá dài.");
        lockSchedule();
        var cls=jdbc.queryForList("SELECT c.*,t.status teacher_status,u.status account_status,u.role account_role FROM learning_class c JOIN staff t ON t.id=c.teacher_id LEFT JOIN app_user u ON u.id=t.user_id WHERE c.id=? FOR UPDATE",input.classId());
        if(cls.isEmpty()) throw error(HttpStatus.NOT_FOUND,"Không tìm thấy lớp học.");
        var selected=cls.getFirst();
        if(!"ACTIVE".equals(selected.get("status")) || !"ACTIVE".equals(selected.get("teacher_status")) || !"ACTIVE".equals(selected.get("account_status")) || !Set.of("ADMIN","TEACHER").contains(String.valueOf(selected.get("account_role")))) throw error(HttpStatus.BAD_REQUEST,"Lớp và tài khoản giáo viên phải đang hoạt động.");
        LocalDate starts=((java.sql.Date)selected.get("starts_on")).toLocalDate(),ends=selected.get("ends_on")==null?null:((java.sql.Date)selected.get("ends_on")).toLocalDate();
        if(input.date().isBefore(starts) || ends!=null && !input.date().isBefore(ends)) throw error(HttpStatus.BAD_REQUEST,"Ngày học nằm ngoài thời gian hoạt động của lớp.");
        String teacher=(String)selected.get("teacher_id");LocalDateTime start=utc(input.date(),input.start()),end=utc(input.date(),input.end());
        if(id!=null) {
            var existing=jdbc.queryForList("SELECT class_id,starts_at,ends_at,teacher_id,status FROM class_session WHERE id=? FOR UPDATE",id);
            if(existing.isEmpty()) throw error(HttpStatus.NOT_FOUND,"Không tìm thấy buổi học.");
            if(!input.classId().equals(existing.getFirst().get("class_id"))) throw error(HttpStatus.BAD_REQUEST,"Không thể đổi lớp của buổi học; hãy tạo buổi học mới.");
            if("CANCELLED".equals(existing.getFirst().get("status"))) throw error(HttpStatus.CONFLICT,"Buổi học đã bị hủy.");
            var old=jdbc.queryForObject("SELECT starts_at,ends_at FROM class_session WHERE id=?",(rs,n)->List.of(rs.getObject(1,LocalDateTime.class),rs.getObject(2,LocalDateTime.class)),id);
            if((!start.equals(old.get(0)) || !end.equals(old.get(1)) || !teacher.equals(existing.getFirst().get("teacher_id"))) && Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM student_attendance WHERE session_id=?)",Boolean.class,id))) throw error(HttpStatus.CONFLICT,"Buổi học đã có điểm danh, không thể thay đổi thời gian hoặc giáo viên.");
        }
        String except=id==null?"":id;
        var overlaps=jdbc.queryForList("SELECT id,class_id,teacher_id,location_note,starts_at FROM class_session WHERE id<>? AND status<>'CANCELLED' AND starts_at<? AND ends_at>? FOR UPDATE",except,end,start);
        var students=roster(input.classId(),input.date());
        for(var other:overlaps) {
            if(input.classId().equals(other.get("class_id"))) throw error(HttpStatus.CONFLICT,"Lớp đã có lịch học trùng giờ.");
            if(teacher.equals(other.get("teacher_id"))) throw error(HttpStatus.CONFLICT,"Giáo viên đã có lịch dạy trùng giờ.");
            if(input.room().strip().equalsIgnoreCase(String.valueOf(other.get("location_note")).strip())) throw error(HttpStatus.CONFLICT,"Phòng học đã có lịch sử dụng trùng giờ.");
            LocalDate otherDay=jdbc.queryForObject("SELECT starts_at FROM class_session WHERE id=?",(rs,n)->local(rs,"starts_at").toLocalDate(),other.get("id"));
            if(roster((String)other.get("class_id"),otherDay).stream().anyMatch(students::contains)) throw error(HttpStatus.CONFLICT,"Học sinh trong lớp đã có lịch học khác trùng giờ.");
        }
        boolean creating=id==null;
        if(creating) {
            id=ShortIds.next(jdbc,"class_session");
            jdbc.update("INSERT INTO class_session(id,class_id,teacher_id,starts_at,ends_at,status,location_note,change_reason,created_by) VALUES(?,?,?,?,?,'SCHEDULED',?,?,?)",id,input.classId(),teacher,start,end,input.room().strip(),input.changeReason(),actor.id());
        } else jdbc.update("UPDATE class_session SET teacher_id=?,starts_at=?,ends_at=?,location_note=?,change_reason=? WHERE id=?",teacher,start,end,input.room().strip(),input.changeReason(),id);
        String action=creating?"CREATE_SESSION":"RESCHEDULE_SESSION";
        if(ShortIds.compactSchema(jdbc))
            jdbc.update("INSERT INTO audit_event(id,actor_id,action,entity_type,entity_id,detail) VALUES(?,?,?,'class_session',?,JSON_OBJECT('class_id',?,'starts_at',?,'ends_at',?))",ShortIds.next(jdbc,"audit_event"),actor.id(),action,id,input.classId(),start.toString(),end.toString());
        else
            jdbc.update("INSERT INTO audit_event(actor_id,action,entity_type,entity_id,detail) VALUES(?,?,'class_session',?,JSON_OBJECT('class_id',?,'starts_at',?,'ends_at',?))",actor.id(),action,id,input.classId(),start.toString(),end.toString());
        return id;
    }
    @Transactional
    public void attendance(String sessionId,AttendanceInput input,AppUserPrincipal actor) {
        active(actor);
        if(!Set.of("ADMIN","TEACHER").contains(actor.role())) throw error(HttpStatus.FORBIDDEN,"Bạn chỉ được xem điểm danh.");
        lockSchedule();
        var rows=jdbc.queryForList("SELECT cs.class_id,cs.teacher_id,cs.status,t.user_id,t.status teacher_status FROM class_session cs JOIN staff t ON t.id=cs.teacher_id WHERE cs.id=? FOR UPDATE",sessionId);
        if(rows.isEmpty()) throw error(HttpStatus.NOT_FOUND,"Không tìm thấy buổi học.");
        var row=rows.getFirst();
        if(actor.role().equals("TEACHER") && (!actor.id().equals(row.get("user_id")) || !"ACTIVE".equals(row.get("teacher_status")))) throw error(HttpStatus.FORBIDDEN,"Bạn không phụ trách buổi học này.");
        if("CANCELLED".equals(row.get("status"))) throw error(HttpStatus.CONFLICT,"Buổi học đã bị hủy.");
        LocalDate day=jdbc.queryForObject("SELECT starts_at FROM class_session WHERE id=?",(rs,n)->local(rs,"starts_at").toLocalDate(),sessionId);
        var roster=roster((String)row.get("class_id"),day);
        if(input==null || input.attendance()==null || !input.attendance().keySet().equals(new HashSet<>(roster)) || input.attendance().values().stream().anyMatch(value->value==null || !Set.of("PRESENT","ABSENT","EXCUSED","LATE").contains(value))) throw error(HttpStatus.BAD_REQUEST,"Vui lòng điểm danh đầy đủ và đúng danh sách học sinh của buổi học.");
        for(String student:roster) {
            String status=input.attendance().get(student);
            int changed=jdbc.update("UPDATE student_attendance SET status=?,recorded_by=?,marked_at=UTC_TIMESTAMP(6) WHERE session_id=? AND student_id=?",status,actor.id(),sessionId,student);
            if(changed==0) jdbc.update("INSERT INTO student_attendance(id,session_id,student_id,status,recorded_by) VALUES(?,?,?,?,?)",ShortIds.next(jdbc,"student_attendance"),sessionId,student,status,actor.id());
        }
    }
    private ResponseStatusException error(HttpStatus status,String message) {return new ResponseStatusException(status,message);}
}

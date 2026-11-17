package com.example.TTGDHNDuongAn.service;

import com.example.TTGDHNDuongAn.repository.ShortIds;
import java.time.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ClassManagementService {
    public record Input(String name,String type,String teacherId,List<String> studentIds,int capacity,String status) {}
    public record SchoolClass(String id,String name,String type,String teacherId,List<String> studentIds,int capacity,String status) {}
    private final JdbcTemplate jdbc;
    public ClassManagementService(JdbcTemplate jdbc) {this.jdbc=jdbc;}
    private LocalDate today() {return LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh"));}
    private String type(String type) {
        if(!Set.of("GROUP","DAYCARE").contains(type==null?"":type)) throw bad("Chỉ hỗ trợ lớp nhóm và lớp bán trú.");
        return type;
    }
    @Transactional(readOnly=true)
    public List<SchoolClass> list(String type) {
        type(type);
        var memberships=jdbc.queryForList("SELECT class_id,student_id FROM enrollment WHERE status='ENROLLED' AND started_on<=? AND (ended_on IS NULL OR ended_on>?)",today(),today());
        return jdbc.query("SELECT id,name,class_type,teacher_id,capacity,status FROM learning_class WHERE class_type=? AND status<>'CLOSED' ORDER BY created_at DESC,id",
            (rs,n)->{
                String classId=rs.getString("id");
                return new SchoolClass(classId,rs.getString("name"),rs.getString("class_type"),rs.getString("teacher_id"),
                    memberships.stream().filter(m->rsId(m,"class_id").equals(classId)).map(m->rsId(m,"student_id")).distinct().toList(),rs.getInt("capacity"),rs.getString("status"));
            },type);
    }
    private String rsId(Map<String,Object> row,String key) {return (String)row.get(key);}
    private void validate(Input input) {
        if(input==null) throw bad("Thiếu thông tin lớp học.");
        type(input.type());
        if(input.name()==null || input.name().isBlank() || input.name().strip().length()>150) throw bad("Tên lớp phải có từ 1 đến 150 ký tự.");
        if(input.status()==null || !Set.of("ACTIVE","PAUSED").contains(input.status())) throw bad("Trạng thái lớp không hợp lệ.");
        if(input.studentIds()==null || input.studentIds().stream().anyMatch(Objects::isNull) || new HashSet<>(input.studentIds()).size()!=input.studentIds().size()) throw bad("Danh sách học sinh không hợp lệ.");
        if(input.capacity()<1 || input.studentIds().size()>input.capacity()) throw bad("Sĩ số không được vượt quá sức chứa của lớp.");
    }
    @Transactional
    public SchoolClass save(String id,Input input) {
        validate(input);
        if(id!=null) {
            var rows=jdbc.queryForList("SELECT class_type,status FROM learning_class WHERE id=? FOR UPDATE",id);
            if(rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Không tìm thấy lớp học.");
            if(!input.type().equals(rows.getFirst().get("class_type")) || "CLOSED".equals(rows.getFirst().get("status"))) throw bad("Không thể đổi hình thức hoặc chỉnh sửa lớp đã đóng.");
        }
        if(input.teacherId()==null || !Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM staff t JOIN app_user u ON u.id=t.user_id WHERE t.id=? AND t.status='ACTIVE' AND u.status='ACTIVE' AND u.role IN ('TEACHER','ADMIN'))",Boolean.class,input.teacherId())))
            throw bad("Vui lòng chọn giáo viên đang làm việc có tài khoản hoạt động.");
        LocalDate day=today();
        var current=id==null?List.<String>of():jdbc.queryForList("SELECT student_id FROM enrollment WHERE class_id=? AND status='ENROLLED' AND started_on<=? AND (ended_on IS NULL OR ended_on>?) FOR UPDATE",String.class,id,day,day);
        // Validate and lock selected students in stable order before changing the roster.
        for(String student:new TreeSet<>(input.studentIds())) {
            var statuses=jdbc.queryForList("SELECT status FROM student WHERE id=? FOR UPDATE",String.class,student);
            if(statuses.isEmpty() || (!"ACTIVE".equals(statuses.getFirst()) && !current.contains(student))) throw bad("Vui lòng chọn học sinh đang học trong danh sách.");
        }
        if(id==null) {
            id=ShortIds.next(jdbc,"learning_class");
            String code=id.length()<=30?id:"LH-"+UUID.randomUUID().toString().substring(0,24);
            jdbc.update("INSERT INTO learning_class(id,code,name,class_type,teacher_id,capacity,starts_on,status) VALUES(?,?,?,?,?,?,?,?)",id,code,input.name().strip(),input.type(),input.teacherId(),input.capacity(),day,input.status());
        } else {
            // Remove students first so a smaller capacity is checked against the final roster.
            for(String student:current) if(!input.studentIds().contains(student)) {
                jdbc.update("UPDATE enrollment SET status=CASE WHEN started_on=? THEN 'CANCELLED' ELSE 'ENDED' END,ended_on=CASE WHEN started_on=? THEN NULL ELSE ? END WHERE class_id=? AND student_id=? AND status='ENROLLED' AND started_on<=? AND (ended_on IS NULL OR ended_on>?)",day,day,day,id,student,day,day);
            }
            jdbc.update("UPDATE learning_class SET name=?,teacher_id=?,capacity=?,status=? WHERE id=?",input.name().strip(),input.teacherId(),input.capacity(),input.status(),id);
        }
        for(String student:input.studentIds()) if(!current.contains(student)) {
            int restored=jdbc.update("UPDATE enrollment SET status='ENROLLED',ended_on=NULL WHERE class_id=? AND student_id=? AND started_on=? AND status='CANCELLED'",id,student,day);
            if(restored==0) jdbc.update("INSERT INTO enrollment(id,class_id,student_id,started_on,status) VALUES(?,?,?,?,'ENROLLED')",ShortIds.next(jdbc,"enrollment"),id,student,day);
        }
        return new SchoolClass(id,input.name().strip(),input.type(),input.teacherId(),List.copyOf(input.studentIds()),input.capacity(),input.status());
    }
    @Transactional
    public void close(String id) {
        var rows=jdbc.queryForList("SELECT class_type FROM learning_class WHERE id=? FOR UPDATE",String.class,id);
        if(rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Không tìm thấy lớp học.");
        type(rows.getFirst());
        jdbc.update("UPDATE learning_class SET status='CLOSED' WHERE id=?",id);
    }
    private ResponseStatusException bad(String message) {return new ResponseStatusException(HttpStatus.BAD_REQUEST,message);}
}

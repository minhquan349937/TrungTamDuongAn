package com.example.TTGDHNDuongAn.service;

import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class StudentProfileService {
    public record GuardianInput(String fullName, String phone, String email, String address, String relationship) {}
    public record ProfileInput(String code, String fullName, LocalDate birthDate, LocalDate admittedOn,
                               String status, String notes, GuardianInput guardian) {}
    public record Guardian(String id, String code, String fullName, String phone, String email, String address, String relationship) {}
    public record Profile(String id, String code, String fullName, LocalDate birthDate, LocalDate admittedOn,
                          String status, String notes, Guardian guardian) {}

    private final JdbcTemplate jdbc;
    public StudentProfileService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    private static final String SELECT = "SELECT s.id,s.code,s.full_name,s.birth_date,s.admitted_on,s.status,s.notes," +
        "g.id guardian_id,g.code guardian_code,g.full_name guardian_name,g.phone,g.email,g.address,sg.relationship " +
        "FROM student s LEFT JOIN student_guardian sg ON sg.student_id=s.id AND sg.is_primary=1 AND sg.revoked_at IS NULL " +
        "LEFT JOIN guardian g ON g.id=sg.guardian_id ";

    private Profile map(ResultSet rs, int n) throws SQLException {
        return new Profile(rs.getString("id"),rs.getString("code"),rs.getString("full_name"),
            rs.getDate("birth_date").toLocalDate(),rs.getDate("admitted_on").toLocalDate(),
            rs.getString("status"),rs.getString("notes"),rs.getString("guardian_id") == null ? null :
                new Guardian(rs.getString("guardian_id"),rs.getString("guardian_code"),rs.getString("guardian_name"),rs.getString("phone"),
                    rs.getString("email"),rs.getString("address"),rs.getString("relationship")));
    }

    public List<Profile> list() {
        return jdbc.query(SELECT+"ORDER BY s.created_at DESC,s.code",this::map);
    }

    public Profile get(String id) {
        return jdbc.query(SELECT+"WHERE s.id=?",this::map,id).stream().findFirst()
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,"Không tìm thấy hồ sơ học sinh."));
    }

    private void validate(ProfileInput input, boolean editing) {
        if (input == null || blank(input.fullName()) || input.birthDate() == null ||
            input.admittedOn() == null || input.guardian() == null || blank(input.guardian().fullName()) ||
            blank(input.guardian().phone()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Cần nhập tên, ngày sinh, ngày nhập học và tên, số điện thoại phụ huynh.");
        if (editing && (blank(input.code()) || input.code().trim().length() > 30))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Mã học sinh không hợp lệ.");
        if (input.fullName().trim().length() > 150 ||
            input.guardian().fullName().trim().length() > 150 || input.guardian().phone().trim().length() > 30 ||
            (input.guardian().email() != null && input.guardian().email().length() > 254))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Thông tin nhập vượt quá độ dài cho phép.");
        if (input.birthDate().isAfter(input.admittedOn()) || input.admittedOn().isAfter(LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh"))))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Ngày sinh và ngày nhập học không hợp lệ.");
        if (editing && (input.status() == null || !List.of("ACTIVE","PAUSED","WITHDRAWN").contains(input.status())))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Trạng thái học sinh không hợp lệ.");
        if (input.guardian().relationship() == null || !List.of("MOTHER","FATHER","GRANDFATHER","GRANDMOTHER","GUARDIAN","OTHER").contains(input.guardian().relationship()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Quan hệ phụ huynh không hợp lệ.");
    }

    private boolean blank(String value) { return value == null || value.isBlank(); }
    private String trimmed(String value) { return value == null ? null : value.trim(); }

    private String nextStudentCode() {
        Long next = jdbc.queryForObject("SELECT next_value FROM student_code_sequence WHERE id=1 FOR UPDATE", Long.class);
        if (next == null) throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,"Chưa khởi tạo bộ đếm mã học sinh.");
        Long maximum = jdbc.queryForObject("SELECT COALESCE(MAX(CAST(SUBSTRING(code,3) AS UNSIGNED)),0) " +
            "FROM student WHERE code REGEXP '^HS[0-9]+$'", Long.class);
        long value = Math.max(next, maximum == null ? 1 : maximum + 1);
        if (value > 999999999L) throw new ResponseStatusException(HttpStatus.CONFLICT,"Đã hết dải mã học sinh.");
        jdbc.update("UPDATE student_code_sequence SET next_value=? WHERE id=1",value + 1);
        return "HS" + String.format(Locale.ROOT,"%03d",value);
    }

    private String nextGuardianCode() {
        Long next = jdbc.queryForObject("SELECT next_value FROM profile_code_sequences WHERE entity_name='guardian' FOR UPDATE",Long.class);
        if (next == null) throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,"Chưa khởi tạo bộ đếm mã phụ huynh.");
        Long maximum = jdbc.queryForObject("SELECT COALESCE(MAX(CAST(SUBSTRING(code,3) AS UNSIGNED)),0) " +
            "FROM guardian WHERE code REGEXP '^PH[0-9]+$'",Long.class);
        long value=Math.max(next,maximum == null ? 1 : maximum+1);
        if (value > 999999999L) throw new ResponseStatusException(HttpStatus.CONFLICT,"Đã hết dải mã phụ huynh.");
        jdbc.update("UPDATE profile_code_sequences SET next_value=? WHERE entity_name='guardian'",value+1);
        return "PH"+String.format(Locale.ROOT,"%03d",value);
    }

    @Transactional
    public Profile create(ProfileInput input) {
        validate(input,false);
        String code=nextStudentCode();
        String guardianCode=nextGuardianCode();
        String studentId=UUID.randomUUID().toString(), guardianId=UUID.randomUUID().toString();
        jdbc.update("INSERT INTO student(id,code,full_name,birth_date,admitted_on,status,notes) VALUES(?,?,?,?,?,?,?)",
            studentId,code,input.fullName().trim(),Date.valueOf(input.birthDate()),Date.valueOf(input.admittedOn()),"ACTIVE",trimmed(input.notes()));
        jdbc.update("INSERT INTO guardian(id,code,full_name,phone,email,address) VALUES(?,?,?,?,?,?)",
            guardianId,guardianCode,input.guardian().fullName().trim(),
            input.guardian().phone().trim(),trimmed(input.guardian().email()),trimmed(input.guardian().address()));
        jdbc.update("INSERT INTO student_guardian(student_id,guardian_id,relationship,is_primary,can_view_portal) VALUES(?,?,?,1,0)",
            studentId,guardianId,input.guardian().relationship());
        return get(studentId);
    }

    @Transactional
    public Profile update(String id, ProfileInput input) {
        validate(input,true);
        Profile old=get(id);
        if ("WITHDRAWN".equals(input.status()) && !"WITHDRAWN".equals(old.status()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Cần nhập ngày nghỉ học trước khi chuyển sang trạng thái ngừng học.");
        if (old.guardian() != null) {
            Integer links=jdbc.queryForObject("SELECT COUNT(*) FROM student_guardian WHERE guardian_id=? AND revoked_at IS NULL",Integer.class,old.guardian().id());
            if (links != null && links > 1)
                throw new ResponseStatusException(HttpStatus.CONFLICT,"Phụ huynh đang liên kết nhiều học sinh; cần sửa trong hồ sơ phụ huynh.");
        }
        jdbc.update("UPDATE student SET code=?,full_name=?,birth_date=?,admitted_on=?,status=?,notes=?," +
            "withdrawn_on=CASE WHEN ?='WITHDRAWN' THEN withdrawn_on ELSE NULL END WHERE id=?",
            input.code().trim(),input.fullName().trim(),Date.valueOf(input.birthDate()),Date.valueOf(input.admittedOn()),input.status(),trimmed(input.notes()),input.status(),id);
        if (old.guardian() == null) {
            String guardianId=UUID.randomUUID().toString();
            jdbc.update("INSERT INTO guardian(id,code,full_name,phone,email,address) VALUES(?,?,?,?,?,?)",
                guardianId,nextGuardianCode(),input.guardian().fullName().trim(),
                input.guardian().phone().trim(),trimmed(input.guardian().email()),trimmed(input.guardian().address()));
            jdbc.update("INSERT INTO student_guardian(student_id,guardian_id,relationship,is_primary,can_view_portal) VALUES(?,?,?,1,0)",
                id,guardianId,input.guardian().relationship());
        } else {
            jdbc.update("UPDATE guardian SET full_name=?,phone=?,email=?,address=? WHERE id=?",
                input.guardian().fullName().trim(),input.guardian().phone().trim(),trimmed(input.guardian().email()),
                trimmed(input.guardian().address()),old.guardian().id());
            jdbc.update("UPDATE student_guardian SET relationship=? WHERE student_id=? AND guardian_id=?",
                input.guardian().relationship(),id,old.guardian().id());
        }
        return get(id);
    }
}

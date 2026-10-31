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
public class StaffProfileService {
    public record Input(String fullName, String position, String specialty, String phone,
                        String email, LocalDate joinedOn, String status) {}
    public record Profile(String id, String code, String fullName, String position, String specialty,
                          String phone, String email, LocalDate joinedOn, String status) {}

    private final JdbcTemplate jdbc;
    public StaffProfileService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    private Profile map(ResultSet rs, int row) throws SQLException {
        return new Profile(rs.getString("id"), rs.getString("code"), rs.getString("full_name"),
            rs.getString("position"), rs.getString("specialty"), rs.getString("phone"),
            rs.getString("email"), rs.getDate("joined_on").toLocalDate(), rs.getString("status"));
    }

    public List<Profile> list() {
        return jdbc.query("SELECT id,code,full_name,position,specialty,phone,email,joined_on,status " +
            "FROM staff ORDER BY created_at DESC,code", this::map);
    }

    private Profile get(String id) {
        return jdbc.query("SELECT id,code,full_name,position,specialty,phone,email,joined_on,status " +
            "FROM staff WHERE id=?", this::map, id).stream().findFirst()
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,"Không tìm thấy hồ sơ nhân viên."));
    }

    private void validate(Input input) {
        if (input == null || input.fullName() == null || input.fullName().isBlank() || input.joinedOn() == null ||
            input.joinedOn().isAfter(LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh"))) ||
            input.status() == null || !List.of("ACTIVE", "ON_LEAVE", "TERMINATED").contains(input.status()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Thông tin nhân viên không hợp lệ.");
        if (input.fullName().trim().length() > 150 || length(input.position()) > 150 ||
            length(input.specialty()) > 150 || length(input.phone()) > 30 || length(input.email()) > 254)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Thông tin nhân viên vượt quá độ dài cho phép.");
    }

    private int length(String value) { return value == null ? 0 : value.trim().length(); }
    private String optional(String value) { return value == null || value.isBlank() ? null : value.trim(); }

    @Transactional
    public Profile create(Input input) {
        validate(input);
        Long next = jdbc.queryForObject("SELECT next_value FROM profile_code_sequences WHERE entity_name='staff' FOR UPDATE", Long.class);
        if (next == null) throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,"Chưa khởi tạo mã nhân viên.");
        Long maximum = jdbc.queryForObject("SELECT COALESCE(MAX(CAST(SUBSTRING(code,3) AS UNSIGNED)),0) " +
            "FROM staff WHERE code REGEXP '^NV[0-9]+$'", Long.class);
        long number = Math.max(next, maximum == null ? 1 : maximum + 1);
        if (number > 999999999L) throw new ResponseStatusException(HttpStatus.CONFLICT,"Đã hết dải mã nhân viên.");
        jdbc.update("UPDATE profile_code_sequences SET next_value=? WHERE entity_name='staff'", number + 1);
        String id = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO staff(id,code,full_name,position,specialty,phone,email,joined_on,status) " +
            "VALUES(?,?,?,?,?,?,?,?,?)", id, "NV" + String.format(Locale.ROOT,"%03d",number),
            input.fullName().trim(), optional(input.position()), optional(input.specialty()),
            optional(input.phone()), optional(input.email()), Date.valueOf(input.joinedOn()), input.status());
        return get(id);
    }

    @Transactional
    public Profile update(String id, Input input) {
        validate(input);
        get(id);
        jdbc.update("UPDATE staff SET full_name=?,position=?,specialty=?,phone=?,email=?,joined_on=?,status=? WHERE id=?",
            input.fullName().trim(), optional(input.position()), optional(input.specialty()),
            optional(input.phone()), optional(input.email()), Date.valueOf(input.joinedOn()), input.status(), id);
        return get(id);
    }
}

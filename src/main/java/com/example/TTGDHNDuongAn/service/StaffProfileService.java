package com.example.TTGDHNDuongAn.service;

import com.example.TTGDHNDuongAn.repository.ShortIds;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class StaffProfileService {
    public record Input(String fullName, String accountRole, String specialty, String phone,
                        String email, LocalDate joinedOn, String status) {}
    public record Profile(String id, String code, String fullName, String position, String specialty,
                          String phone, String email, LocalDate joinedOn, String status, String accountRole) {}

    private final JdbcTemplate jdbc;
    private final AccountContactService accounts;
    public StaffProfileService(JdbcTemplate jdbc, AccountContactService accounts) {
        this.jdbc = jdbc;
        this.accounts = accounts;
    }

    private Profile map(ResultSet rs, int row) throws SQLException {
        return new Profile(rs.getString("id"), rs.getString("code"), rs.getString("full_name"),
            rs.getString("position"), rs.getString("specialty"), rs.getString("phone"),
            rs.getString("email"), rs.getDate("joined_on").toLocalDate(), rs.getString("status"), rs.getString("account_role"));
    }

    public List<Profile> list() {
        return jdbc.query("SELECT id,code,full_name,position,specialty,phone,email,joined_on,status,account_role " +
            "FROM staff ORDER BY created_at DESC,code", this::map);
    }

    private Profile get(String id) {
        return jdbc.query("SELECT id,code,full_name,position,specialty,phone,email,joined_on,status,account_role " +
            "FROM staff WHERE id=?", this::map, id).stream().findFirst()
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,"Không tìm thấy hồ sơ nhân viên."));
    }

    private void validate(Input input) {
        if (input == null || input.fullName() == null || input.fullName().isBlank() || input.joinedOn() == null ||
            input.joinedOn().isAfter(LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh"))) ||
            input.status() == null || !List.of("ACTIVE", "ON_LEAVE", "TERMINATED").contains(input.status()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Thông tin nhân viên không hợp lệ.");
        if (input.accountRole() == null || !List.of("ADMIN", "TEACHER").contains(input.accountRole()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Chức vụ phải là Quản trị viên hoặc Giáo viên.");
        if (input.email() == null || input.email().isBlank() ||
            !input.email().trim().matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$"))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Vui lòng nhập email đăng nhập hợp lệ.");
        if (input.fullName().trim().length() > 150 ||
            length(input.specialty()) > 150 || length(input.phone()) > 30 || length(input.email()) > 254)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Thông tin nhân viên vượt quá độ dài cho phép.");
    }

    private int length(String value) { return value == null ? 0 : value.trim().length(); }
    private String optional(String value) { return value == null || value.isBlank() ? null : value.trim(); }

    private String email(Input input) { return input.email().trim().toLowerCase(Locale.ROOT); }
    private String position(Input input) { return "ADMIN".equals(input.accountRole()) ? "Quản trị viên" : "Giáo viên"; }

    private void requireAvailableEmail(Input input, String userId) {
        accounts.available(email(input),input.phone(),userId);
    }

    private String createAccount(Input input) {
        return accounts.create(input.fullName(),email(input),input.phone(),input.accountRole());
    }

    @Transactional
    public Profile create(Input input) {
        validate(input);
        requireAvailableEmail(input, null);
        Long next = jdbc.queryForObject("SELECT next_value FROM profile_code_sequences WHERE entity_name='staff' FOR UPDATE", Long.class);
        if (next == null) throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,"Chưa khởi tạo mã nhân viên.");
        Long maximum = jdbc.queryForObject("SELECT COALESCE(MAX(CAST(SUBSTRING(code,3) AS UNSIGNED)),0) " +
            "FROM staff WHERE code REGEXP '^NV[0-9]+$'", Long.class);
        long number = Math.max(next, maximum == null ? 1 : maximum + 1);
        if (number > 999999999L) throw new ResponseStatusException(HttpStatus.CONFLICT,"Đã hết dải mã nhân viên.");
        jdbc.update("UPDATE profile_code_sequences SET next_value=? WHERE entity_name='staff'", number + 1);
        String id = ShortIds.next(jdbc, "staff");
        String userId = createAccount(input);
        jdbc.update("INSERT INTO staff(id,code,user_id,account_role,full_name,position,specialty,phone,email,joined_on,status) " +
            "VALUES(?,?,?,?,?,?,?,?,?,?,?)", id, "NV" + String.format(Locale.ROOT,"%03d",number), userId, input.accountRole(),
            input.fullName().trim(), position(input), optional(input.specialty()),
            LoginIdentifiers.phone(input.phone()), email(input), Date.valueOf(input.joinedOn()), input.status());
        return get(id);
    }

    @Transactional
    public Profile update(String id, Input input) {
        validate(input);
        var links = jdbc.query("SELECT user_id FROM staff WHERE id=? FOR UPDATE", (rs, n) -> rs.getString("user_id"), id);
        if (links.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Không tìm thấy hồ sơ nhân viên.");
        String userId = links.getFirst();
        requireAvailableEmail(input, userId);
        if (userId == null) userId = createAccount(input);
        else {
            // Release the composite FK while changing the referenced account role in this transaction.
            jdbc.update("UPDATE staff SET user_id=NULL WHERE id=?", id);
            accounts.update(userId,input.fullName(),email(input),input.phone());
            jdbc.update("UPDATE app_user SET role=? WHERE id=?",input.accountRole(),userId);
        }
        jdbc.update("UPDATE staff SET full_name=?,position=?,specialty=?,phone=?,email=?,joined_on=?,status=?,user_id=?,account_role=? WHERE id=?",
            input.fullName().trim(), position(input), optional(input.specialty()),
            LoginIdentifiers.phone(input.phone()), email(input), Date.valueOf(input.joinedOn()), input.status(), userId, input.accountRole(), id);
        return get(id);
    }
}

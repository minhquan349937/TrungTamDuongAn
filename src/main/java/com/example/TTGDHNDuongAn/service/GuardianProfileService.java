package com.example.TTGDHNDuongAn.service;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class GuardianProfileService {
    public record GuardianInput(String fullName, String phone, String email, String address) {}
    public record Guardian(String id, String code, String fullName, String phone, String email, String address) {}

    private final JdbcTemplate jdbc;
    public GuardianProfileService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Transactional
    public Guardian update(String id, GuardianInput input) {
        if (input == null || input.fullName() == null || input.fullName().isBlank() ||
            input.phone() == null || input.phone().isBlank())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Cần nhập tên và số điện thoại phụ huynh.");
        if (input.fullName().trim().length() > 150 || input.phone().trim().length() > 30 ||
            (input.email() != null && input.email().trim().length() > 254))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Thông tin phụ huynh vượt quá độ dài cho phép.");
        int updated = jdbc.update("UPDATE guardian SET full_name=?,phone=?,email=?,address=? WHERE id=?",
            input.fullName().trim(),input.phone().trim(),emptyToNull(input.email()),emptyToNull(input.address()),id);
        if (updated == 0)
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Không tìm thấy hồ sơ phụ huynh.");
        return jdbc.query("SELECT id,code,full_name,phone,email,address FROM guardian WHERE id=?",
            (rs,n)->new Guardian(rs.getString("id"),rs.getString("code"),rs.getString("full_name"),rs.getString("phone"),
                rs.getString("email"),rs.getString("address")),id).getFirst();
    }

    private String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}

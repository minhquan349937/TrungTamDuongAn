package com.example.TTGDHNDuongAn.repository;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

import com.example.TTGDHNDuongAn.model.AppUser;
import com.example.TTGDHNDuongAn.service.LoginIdentifiers;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AppUserRepository {
    private final JdbcTemplate jdbcTemplate;

    public AppUserRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<AppUser> findActiveByEmail(String email) {
        return findActiveByLogin(email);
    }

    public Optional<AppUser> findActiveByLogin(String login) {
        String normalized=LoginIdentifiers.login(login);
        List<AppUser> users = jdbcTemplate.query(
                "SELECT id, email, display_name, role, password_hash, status, phone " +
                        "FROM app_user WHERE "+(normalized.contains("@")?"email":"phone")+" = ? AND status = 'ACTIVE'",
                (rs, rowNum) -> new AppUser(
                        rs.getString("id"), rs.getString("email"), rs.getString("display_name"),
                        rs.getString("role"), rs.getString("password_hash"), rs.getString("status"),rs.getString("phone")),
                normalized);
        return users.stream().findFirst();
    }

    public void insertIfAbsent(AppUser user) {
        jdbcTemplate.update(
                "INSERT IGNORE INTO app_user (id, email, password_hash, display_name, role, status) " +
                        "VALUES (?, ?, ?, ?, ?, 'ACTIVE')",
                ShortIds.next(jdbcTemplate, "app_user"), normalizeEmail(user.email()), user.passwordHash(),
                user.displayName(), user.role());
    }

    public void updateLastLoginAt(String userId) {
        jdbcTemplate.update("UPDATE app_user SET last_login_at = UTC_TIMESTAMP(6) WHERE id = ?", userId);
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}

package com.example.TTGDHNDuongAn;

import com.example.TTGDHNDuongAn.service.StaffProfileService;
import com.example.TTGDHNDuongAn.service.AccountContactService;
import com.example.TTGDHNDuongAn.service.StaffProfileService.Input;
import java.time.LocalDate;
import com.example.TTGDHNDuongAn.repository.ShortIds;
import org.mockito.MockedStatic;
import static org.mockito.Mockito.mockStatic;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class StaffProfileServiceTests {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final AccountContactService accounts=mock(AccountContactService.class);
    private final StaffProfileService service = new StaffProfileService(jdbc, accounts);

    @Test void rejectsInvalidStaffBeforeWriting() {
        var input = new Input("  ", null, null, null, null, LocalDate.of(2024,1,1), "ACTIVE");
        var error = assertThrows(ResponseStatusException.class, () -> service.create(input));
        assertEquals(400, error.getStatusCode().value());
        verifyNoInteractions(jdbc);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ADMIN", "TEACHER"})
    void createsStaffWithNextDatabaseCode(String role) {
        try (MockedStatic<ShortIds> ids = mockStatic(ShortIds.class)) {
            ids.when(() -> ShortIds.next(jdbc, "staff")).thenReturn("NV003");
            ids.when(() -> ShortIds.next(jdbc, "app_user")).thenReturn("TK003");
        var input = new Input("Nguyễn Văn An", role, "Can thiệp sớm", null, " An@Example.com ",
            LocalDate.of(2024,1,1), "ACTIVE");
        when(accounts.create(anyString(),anyString(),isNull(),eq(role))).thenReturn("TK003");
        when(jdbc.queryForObject("SELECT next_value FROM profile_code_sequences WHERE entity_name='staff' FOR UPDATE", Long.class))
            .thenReturn(1L);
        when(jdbc.queryForObject(startsWith("SELECT COALESCE(MAX(CAST(SUBSTRING(code,3)"), eq(Long.class)))
            .thenReturn(2L);
        String position = role.equals("ADMIN") ? "Quản trị viên" : "Giáo viên";
        var saved = new StaffProfileService.Profile("id", "NV003", input.fullName(), position,
            input.specialty(), null, "an@example.com", input.joinedOn(), input.status(), role);
        when(jdbc.query(startsWith("SELECT id,code,full_name"), any(RowMapper.class), anyString()))
            .thenReturn(List.of(saved));
        assertEquals("NV003", service.create(input).code());
        verify(jdbc).update("UPDATE profile_code_sequences SET next_value=? WHERE entity_name='staff'", 4L);
        verify(accounts).create("Nguyễn Văn An","an@example.com",null,role);
        verify(jdbc).update(startsWith("INSERT INTO staff("), anyString(), eq("NV003"), eq("TK003"), eq(role), eq("Nguyễn Văn An"),
            eq(position), eq("Can thiệp sớm"), isNull(), eq("an@example.com"), any(), eq("ACTIVE"));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "invalid", "a b@example.com"})
    void rejectsMissingOrInvalidLoginEmail(String email) {
        var input = new Input("An", "TEACHER", null, null, email, LocalDate.of(2024,1,1), "ACTIVE");
        assertEquals(400, assertThrows(ResponseStatusException.class, () -> service.create(input)).getStatusCode().value());
        verifyNoInteractions(jdbc);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "PARENT", "Giáo viên"})
    void rejectsOtherRoles(String role) {
        var input = new Input("An", role, null, null, "an@example.com", LocalDate.of(2024,1,1), "ACTIVE");
        assertEquals(400, assertThrows(ResponseStatusException.class, () -> service.create(input)).getStatusCode().value());
        verifyNoInteractions(jdbc);
    }

    @Test void rejectsExistingLoginWithoutCreatingStaff() {
        doThrow(new ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT))
            .when(accounts).available(anyString(),isNull(),isNull());
        var input = new Input("An", "ADMIN", null, null, "an@example.com", LocalDate.of(2024,1,1), "ACTIVE");
        assertEquals(409, assertThrows(ResponseStatusException.class, () -> service.create(input)).getStatusCode().value());
        verify(jdbc, never()).update(anyString(), any(Object[].class));
    }

    @Test void updatesLinkedAccountWithoutResettingPassword() {
        var input = new Input("An", "ADMIN", null, null, "AN@example.com", LocalDate.of(2024,1,1), "ACTIVE");
        when(jdbc.query(startsWith("SELECT user_id FROM staff"), any(RowMapper.class), eq("NV003")))
            .thenReturn(List.of("TK003"));
        var saved = new StaffProfileService.Profile("NV003", "NV003", "An", "Quản trị viên", null, null,
            "an@example.com", input.joinedOn(), "ACTIVE", "ADMIN");
        when(jdbc.query(startsWith("SELECT id,code,full_name"), any(RowMapper.class), eq("NV003")))
            .thenReturn(List.of(saved));
        service.update("NV003", input);
        var order = inOrder(jdbc);
        order.verify(jdbc).update("UPDATE staff SET user_id=NULL WHERE id=?", "NV003");
        verify(accounts).update("TK003","An","an@example.com",null);
        order.verify(jdbc).update("UPDATE app_user SET role=? WHERE id=?", "ADMIN", "TK003");
        order.verify(jdbc).update(startsWith("UPDATE staff SET full_name="), eq("An"), eq("Quản trị viên"),
            isNull(), isNull(), eq("an@example.com"), any(), eq("ACTIVE"), eq("TK003"), eq("ADMIN"), eq("NV003"));
    }
}

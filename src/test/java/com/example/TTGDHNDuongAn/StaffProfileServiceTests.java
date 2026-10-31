package com.example.TTGDHNDuongAn;

import com.example.TTGDHNDuongAn.service.StaffProfileService;
import com.example.TTGDHNDuongAn.service.StaffProfileService.Input;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class StaffProfileServiceTests {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final StaffProfileService service = new StaffProfileService(jdbc);

    @Test void rejectsInvalidStaffBeforeWriting() {
        var input = new Input("  ", null, null, null, null, LocalDate.of(2024,1,1), "ACTIVE");
        var error = assertThrows(ResponseStatusException.class, () -> service.create(input));
        assertEquals(400, error.getStatusCode().value());
        verifyNoInteractions(jdbc);
    }

    @Test void createsStaffWithNextDatabaseCode() {
        var input = new Input("Nguyễn Văn An", "Giáo viên", "Can thiệp sớm", null, null,
            LocalDate.of(2024,1,1), "ACTIVE");
        when(jdbc.queryForObject("SELECT next_value FROM profile_code_sequences WHERE entity_name='staff' FOR UPDATE", Long.class))
            .thenReturn(1L);
        when(jdbc.queryForObject(startsWith("SELECT COALESCE(MAX(CAST(SUBSTRING(code,3)"), eq(Long.class)))
            .thenReturn(2L);
        var saved = new StaffProfileService.Profile("id", "NV003", input.fullName(), input.position(),
            input.specialty(), null, null, input.joinedOn(), input.status());
        when(jdbc.query(startsWith("SELECT id,code,full_name"), any(RowMapper.class), anyString()))
            .thenReturn(List.of(saved));
        assertEquals("NV003", service.create(input).code());
        verify(jdbc).update("UPDATE profile_code_sequences SET next_value=? WHERE entity_name='staff'", 4L);
        verify(jdbc).update(startsWith("INSERT INTO staff("), anyString(), eq("NV003"), eq("Nguyễn Văn An"),
            eq("Giáo viên"), eq("Can thiệp sớm"), isNull(), isNull(), any(), eq("ACTIVE"));
    }
}

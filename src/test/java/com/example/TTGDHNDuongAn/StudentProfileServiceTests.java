package com.example.TTGDHNDuongAn;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.*;

import com.example.TTGDHNDuongAn.service.StudentProfileService;
import com.example.TTGDHNDuongAn.service.StudentProfileService.GuardianInput;
import com.example.TTGDHNDuongAn.service.StudentProfileService.ProfileInput;
import com.example.TTGDHNDuongAn.service.StudentProfileService.Profile;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;

class StudentProfileServiceTests {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final StudentProfileService service = new StudentProfileService(jdbc);
    private final GuardianInput guardian = new GuardianInput("Nguyễn Thị Mẹ", "0912345678", null, null, "MOTHER");

    @Test void rejectsInvalidDatesBeforeWritingAnything() {
        var input = new ProfileInput("HS100", "Nguyễn Văn A", LocalDate.of(2026, 10, 28),
            LocalDate.of(2026, 10, 27), "ACTIVE", null, guardian);
        var error = assertThrows(ResponseStatusException.class, () -> service.create(input));
        assertEquals(400, error.getStatusCode().value());
        verifyNoInteractions(jdbc);
    }

    @Test void requiresGuardianBeforeWritingAnything() {
        var input = new ProfileInput("HS100", "Nguyễn Văn A", LocalDate.of(2022, 1, 1),
            LocalDate.of(2024, 1, 1), "ACTIVE", null, null);
        var error = assertThrows(ResponseStatusException.class, () -> service.create(input));
        assertEquals(400, error.getStatusCode().value());
        verifyNoInteractions(jdbc);
    }

    @Test void generatesNextCodeAndActiveStatusWithoutClientValues() {
        var input = new ProfileInput(null, "Nguyễn Văn A", LocalDate.of(2022, 1, 1),
            LocalDate.of(2024, 1, 1), null, null,
            new GuardianInput("Nguyễn Văn Ông", "0912345678", null, null, "GRANDFATHER"));
        var subject = spy(service);
        when(jdbc.queryForObject("SELECT next_value FROM student_code_sequence WHERE id=1 FOR UPDATE",Long.class)).thenReturn(1L);
        when(jdbc.queryForObject(startsWith("SELECT COALESCE(MAX(CAST(SUBSTRING(code,3)"),eq(Long.class))).thenReturn(2L);
        doReturn(new Profile("id","HS003","Nguyễn Văn A",input.birthDate(),input.admittedOn(),"ACTIVE",null,null))
            .when(subject).get(anyString());

        Profile saved = subject.create(input);

        assertEquals("HS003",saved.code());
        assertEquals("ACTIVE",saved.status());
        verify(jdbc).update("UPDATE student_code_sequence SET next_value=? WHERE id=1",4L);
        verify(jdbc).update(startsWith("INSERT INTO student("),anyString(),eq("HS003"),eq("Nguyễn Văn A"),
            any(),any(),eq("ACTIVE"),isNull());
        verify(jdbc).update(startsWith("INSERT INTO student_guardian("),anyString(),anyString(),eq("GRANDFATHER"));
    }
}

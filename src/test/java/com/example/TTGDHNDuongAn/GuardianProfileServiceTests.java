package com.example.TTGDHNDuongAn;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.example.TTGDHNDuongAn.service.GuardianProfileService;
import com.example.TTGDHNDuongAn.service.GuardianProfileService.GuardianInput;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;

class GuardianProfileServiceTests {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final GuardianProfileService service = new GuardianProfileService(jdbc,mock(com.example.TTGDHNDuongAn.service.AccountContactService.class));

    @Test void requiresContactFieldsBeforeWriting() {
        var error = assertThrows(ResponseStatusException.class,
            () -> service.update("guardian-id",new GuardianInput(" ","0912345678",null,null)));
        assertEquals(400,error.getStatusCode().value());
        verifyNoInteractions(jdbc);
    }

    @Test void doesNotCreateAnUnknownGuardian() {
        var error = assertThrows(ResponseStatusException.class,
            () -> service.update("missing",new GuardianInput("Nguyễn Thị Mai","0912345678",null,null)));
        assertEquals(404,error.getStatusCode().value());
        verify(jdbc).query(eq("SELECT user_id FROM guardian WHERE id=? FOR UPDATE"),any(org.springframework.jdbc.core.RowMapper.class),eq("missing"));
        verifyNoMoreInteractions(jdbc);
    }
}

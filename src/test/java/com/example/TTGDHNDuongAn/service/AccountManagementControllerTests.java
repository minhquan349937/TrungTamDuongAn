package com.example.TTGDHNDuongAn.service;

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.example.TTGDHNDuongAn.controller.AccountManagementController;
import com.example.TTGDHNDuongAn.controller.StudentProfileExceptionHandler;
import com.example.TTGDHNDuongAn.security.SecurityConfig;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(value=AccountManagementController.class,properties={"debug=false","logging.level.root=WARN"})
@Import({SecurityConfig.class,StudentProfileExceptionHandler.class})
class AccountManagementControllerTests {
    @Autowired MockMvc mvc;
    @MockitoBean AccountManagementService service;
    @MockitoBean UserDetailsService users;
    @MockitoBean JdbcTemplate jdbc;
    final String url="/api/admin/accounts/u1";
    final String body="""
        {"displayName":"Updated name","email":"new@example.com","phone":"0912345678","role":"ADMIN","status":"DISABLED","password":"new-password"}
        """;
    @Test void onlyAdminWithCsrfCanUpdateAccounts() throws Exception {
        mvc.perform(put(url).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isUnauthorized());
        for(String role:List.of("TEACHER","PARENT"))
            mvc.perform(put(url).with(user("actor").roles(role)).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden());
        mvc.perform(put(url).with(user("admin").roles("ADMIN")).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }
    @Test void savesAllFieldsAndDoesNotReturnPassword() throws Exception {
        var input=new AccountManagementService.Update("Updated name","new@example.com","0912345678","ADMIN","DISABLED","new-password");
        when(service.updateAccount("u1",input)).thenReturn(new AccountManagementService.Account("u1",input.displayName(),input.email(),input.phone(),input.role(),input.status(),List.of("ADMIN","TEACHER")));
        mvc.perform(put(url).with(user("admin").roles("ADMIN")).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isOk()).andExpect(jsonPath("$.role").value("ADMIN"))
            .andExpect(jsonPath("$.status").value("DISABLED")).andExpect(jsonPath("$.password").doesNotExist())
            .andExpect(jsonPath("$.passwordHash").doesNotExist());
        verify(service).updateAccount("u1",input);
    }
}

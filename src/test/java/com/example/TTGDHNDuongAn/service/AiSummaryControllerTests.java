package com.example.TTGDHNDuongAn.service;

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.example.TTGDHNDuongAn.controller.AiSummaryController;
import com.example.TTGDHNDuongAn.security.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.*;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

@WebMvcTest(value=AiSummaryController.class,properties={"debug=false","logging.level.root=WARN"})
@Import(SecurityConfig.class)
class AiSummaryControllerTests {
    @Autowired MockMvc mvc;
    @MockitoBean AiSummaryDataService data;
    @MockitoBean GeminiSummaryClient client;
    @MockitoBean AiSummaryQuota quota;
    @MockitoBean UserDetailsService users;
    AppUserPrincipal actor(String role) { return new AppUserPrincipal("TK000001","test@invalid.test","Test",role,"unused"); }
    final String url="/api/learning-plans/KH000001/ai-summary";
    @Test void teacherAndAdminCanGenerateWithoutPersisting() throws Exception {
        var context=new AiSummaryDataService.Context("ACTIVE",List.of());
        when(data.load(eq("KH000001"),any())).thenReturn(context);
        when(client.generate(context,"")).thenReturn("Gợi ý giả lập");
        for(String role:List.of("TEACHER","ADMIN")) {
            mvc.perform(post(url).with(user(actor(role))).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"keywords\":\"  \"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.generatedText").value("Gợi ý giả lập"));
        }
        verify(quota,times(2)).acquire("TK000001");
    }
    @Test void authenticationRoleAndCsrfAreRequiredBeforeAnyDataOrExternalCall() throws Exception {
        mvc.perform(post(url).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{}" )).andExpect(status().isUnauthorized());
        mvc.perform(post(url).with(user(actor("PARENT"))).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{}" )).andExpect(status().isForbidden());
        mvc.perform(post(url).with(user(actor("TEACHER"))).contentType(MediaType.APPLICATION_JSON).content("{}" )).andExpect(status().isForbidden());
        verifyNoInteractions(data,client,quota);
    }
    @Test void unassignedTeacherAndLongKeywordsCannotCallGemini() throws Exception {
        when(data.load(eq("KH000001"),any())).thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN,"Bạn chưa được phân công phụ trách học sinh này."));
        mvc.perform(post(url).with(user(actor("TEACHER"))).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isForbidden());
        mvc.perform(post(url).with(user(actor("TEACHER"))).with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content("{\"keywords\":\""+"x".repeat(2001)+"\"}"))
            .andExpect(status().isBadRequest());
        verifyNoInteractions(client,quota);
    }
    @Test void safeProviderErrorsReachFrontend() throws Exception {
        var context=new AiSummaryDataService.Context("ACTIVE",List.of());
        when(data.load(eq("KH000001"),any())).thenReturn(context);
        when(client.generate(context,"")).thenThrow(new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,"Gemini đã hết hạn mức."));
        mvc.perform(post(url).with(user(actor("ADMIN"))).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isTooManyRequests()).andExpect(jsonPath("$.message").value("Gemini đã hết hạn mức."));
    }
}

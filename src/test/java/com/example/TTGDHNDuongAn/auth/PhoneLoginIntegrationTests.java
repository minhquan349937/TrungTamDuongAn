package com.example.TTGDHNDuongAn.auth;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.example.TTGDHNDuongAn.repository.ShortIds;
import com.example.TTGDHNDuongAn.security.AppUserPrincipal;
import com.example.TTGDHNDuongAn.service.*;
import com.example.TTGDHNDuongAn.service.GuardianProfileService.GuardianInput;
import com.example.TTGDHNDuongAn.service.AccountManagementService.Contact;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(properties={"spring.datasource.url=${PHONE_LOGIN_TEST_DB_URL}","SETUP_ADMIN_EMAIL=","SETUP_ADMIN_PASSWORD=",
    "spring.docker.compose.enabled=false","logging.level.root=WARN","debug=false"})
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named="PHONE_LOGIN_TEST_DB_URL",matches=".+")
@Transactional
class PhoneLoginIntegrationTests {
    @Autowired GuardianProfileService guardians;
    @Autowired StudentProfileService students;
    @Autowired StaffProfileService staff;
    @Autowired AccountManagementService accounts;
    @Autowired AuthService auth;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder encoder;
    @Autowired MockMvc mvc;
    final ObjectMapper json=new ObjectMapper();
    String phone() {return "09"+String.format("%08d",Integer.toUnsignedLong(UUID.randomUUID().hashCode())%100000000);}
    String email() {return UUID.randomUUID()+"@phone-test.invalid";}
    GuardianInput parent(String phone,String email) {return new GuardianInput("Phụ huynh giả lập",phone,email,"Địa chỉ giả lập");}
    String userId(String guardian) {return jdbc.queryForObject("SELECT user_id FROM guardian WHERE id=?",String.class,guardian);}
    AppUserPrincipal actor(String role) {return new AppUserPrincipal("TK000001","test@invalid.test","Test",role,"unused");}
    StudentProfileService.ProfileInput child(String name,String phone,String email) {
        return new StudentProfileService.ProfileInput(null,name,LocalDate.of(2022,1,1),LocalDate.of(2024,1,1),null,null,
            new StudentProfileService.GuardianInput("Phụ huynh giả lập",phone,email,"Địa chỉ giả lập","MOTHER"));
    }
    @Test void phoneOnlyParentGetsLinkedAccountAndCanLogInThroughHttpWithCorrectRole() throws Exception {
        String phone=phone();var guardian=guardians.create(parent(phone,null));
        var account=jdbc.queryForMap("SELECT * FROM app_user WHERE id=?",userId(guardian.id()));
        assertNull(account.get("email"));assertEquals(phone,account.get("phone"));assertEquals("PARENT",account.get("role"));
        assertEquals(guardian.fullName(),account.get("display_name"));assertTrue(encoder.matches("123456",(String)account.get("password_hash")));
        var login=mvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(java.util.Map.of("login",phone,"password","123456"))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.role").value("PARENT")).andExpect(jsonPath("$.phone").value(phone)).andReturn();
        var session=(MockHttpSession)login.getRequest().getSession(false);
        mvc.perform(get("/api/auth/me").session(session)).andExpect(status().isOk()).andExpect(jsonPath("$.displayName").value(guardian.fullName()));
        mvc.perform(get("/api/admin/accounts").session(session)).andExpect(status().isForbidden());
        mvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(java.util.Map.of("login",phone,"password","wrong"))))
            .andExpect(status().isUnauthorized());
    }
    @Test void emailAndFormattedVietnamPhoneAuthenticateSameAccountAndLegacyRequestStillWorks() throws Exception {
        String phone=phone(),email=email();var guardian=guardians.create(parent("+84 "+phone.substring(1),email.toUpperCase()));
        assertEquals(phone,guardian.phone());assertEquals(email,guardian.email());
        assertEquals(userId(guardian.id()),auth.currentUser(auth.authenticate(phone,"123456")).id());
        assertEquals(userId(guardian.id()),auth.currentUser(auth.authenticate("+84 "+phone.substring(1),"123456")).id());
        mvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(java.util.Map.of("email",email.toUpperCase(),"password","123456"))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.role").value("PARENT"));
    }
    @Test void addingStudentCreatesParentAccountAndSiblingsReuseItWithoutResettingPassword() {
        String phone=phone();var first=students.create(child("Học sinh giả lập một",phone,null));
        String accountId=userId(first.guardian().id()),custom=encoder.encode("changed-password");
        jdbc.update("UPDATE app_user SET password_hash=? WHERE id=?",custom,accountId);
        var second=students.create(child("Học sinh giả lập hai",phone,null));
        assertEquals(first.guardian().id(),second.guardian().id());
        assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM student_guardian WHERE guardian_id=?",Integer.class,first.guardian().id()));
        assertEquals(custom,jdbc.queryForObject("SELECT password_hash FROM app_user WHERE id=?",String.class,accountId));
        assertEquals("PARENT",auth.currentUser(auth.authenticate(phone,"changed-password")).role());
        assertEquals(0,jdbc.queryForObject("SELECT can_view_portal FROM student_guardian WHERE student_id=?",Integer.class,first.id()));
    }
    @Test void existingPhoneCannotBeAttachedToDifferentParentOrTeacher() {
        String phone=phone(),email=email();
        staff.create(new StaffProfileService.Input("Giáo viên giả lập","TEACHER",null,phone,email,LocalDate.of(2024,1,1),"ACTIVE"));
        int count=jdbc.queryForObject("SELECT COUNT(*) FROM app_user",Integer.class);
        var error=assertThrows(ResponseStatusException.class,()->guardians.create(parent(phone,null)));
        assertEquals(409,error.getStatusCode().value());assertEquals(count,jdbc.queryForObject("SELECT COUNT(*) FROM app_user",Integer.class));
    }
    @Test void guardianEditSynchronizesLoginAndPreservesPasswordAndRole() {
        String original=phone(),updatedPhone=phone(),updatedEmail=email();
        var guardian=guardians.create(parent(original,null));String id=userId(guardian.id());
        String password=encoder.encode("custom-password");jdbc.update("UPDATE app_user SET password_hash=? WHERE id=?",password,id);
        guardians.update(guardian.id(),new GuardianInput("Tên đã cập nhật",updatedPhone,updatedEmail,null));
        assertEquals(id,userId(guardian.id()));assertEquals(password,jdbc.queryForObject("SELECT password_hash FROM app_user WHERE id=?",String.class,id));
        assertEquals("Tên đã cập nhật",auth.currentUser(auth.authenticate(updatedEmail,"custom-password")).displayName());
        assertEquals("PARENT",auth.currentUser(auth.authenticate(updatedPhone,"custom-password")).role());
        assertThrows(BadCredentialsException.class,()->auth.authenticate(original,"custom-password"));
    }
    @Test void adminContactsSynchronizeProfilesAndRejectDuplicateWithoutChangingRoleOrPassword() {
        String phone=phone(),another=phone();var first=guardians.create(parent(phone,null));var second=guardians.create(parent(another,null));
        String id=userId(first.id()),email=email(),changed=phone();
        var account=accounts.update(id,new Contact(email,changed));
        assertEquals("PARENT",account.role());assertEquals(changed,jdbc.queryForObject("SELECT phone FROM guardian WHERE id=?",String.class,first.id()));
        assertEquals(email,jdbc.queryForObject("SELECT email FROM guardian WHERE id=?",String.class,first.id()));
        assertEquals(id,auth.currentUser(auth.authenticate(changed,"123456")).id());
        assertEquals(409,assertThrows(ResponseStatusException.class,()->accounts.update(id,new Contact(email,another))).getStatusCode().value());
        assertEquals(changed,jdbc.queryForObject("SELECT phone FROM app_user WHERE id=?",String.class,id));
        assertEquals(400,assertThrows(ResponseStatusException.class,()->accounts.update(userId(second.id()),new Contact(null,null))).getStatusCode().value());
    }
    @Test void disabledAndLockedAccountsCannotUseEitherIdentifier() {
        String phone=phone(),email=email();var guardian=guardians.create(parent(phone,email));
        for(String status:new String[]{"DISABLED","LOCKED"}) {
            jdbc.update("UPDATE app_user SET status=? WHERE id=?",status,userId(guardian.id()));
            assertThrows(BadCredentialsException.class,()->auth.authenticate(phone,"123456"));
            assertThrows(BadCredentialsException.class,()->auth.authenticate(email,"123456"));
        }
    }
    @Test void adminApisAndCsrfAreEnforcedAndNoHashIsReturned() throws Exception {
        String phone=phone();var guardian=guardians.create(parent(phone,null));String id=userId(guardian.id());
        mvc.perform(get("/api/admin/accounts")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/accounts").with(user(actor("TEACHER")))).andExpect(status().isForbidden());
        var response=mvc.perform(get("/api/admin/accounts").with(user(actor("ADMIN")))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertFalse(response.contains("password_hash"));assertFalse(response.contains("passwordHash"));assertTrue(response.contains(phone));
        String url="/api/admin/accounts/"+id+"/contact",body=json.writeValueAsString(new Contact(null,phone));
        mvc.perform(put(url).with(user(actor("ADMIN"))).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden());
        mvc.perform(put(url).with(user(actor("ADMIN"))).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isOk()).andExpect(jsonPath("$.role").value("PARENT"));
        mvc.perform(post("/api/guardians").with(user(actor("TEACHER"))).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(parent(phone(),null))))
            .andExpect(status().isForbidden());
        mvc.perform(post("/api/guardians").with(user(actor("ADMIN"))).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(parent(phone(),null))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.phone").isNotEmpty());
    }
    @Test void invalidPhoneOrEmailCannotCreateOrChangeAnAccount() {
        int before=jdbc.queryForObject("SELECT COUNT(*) FROM app_user",Integer.class);
        assertEquals(400,assertThrows(ResponseStatusException.class,()->guardians.create(parent("not-phone",null))).getStatusCode().value());
        assertEquals(400,assertThrows(ResponseStatusException.class,()->guardians.create(parent(phone(),"bad-email"))).getStatusCode().value());
        assertEquals(before,jdbc.queryForObject("SELECT COUNT(*) FROM app_user",Integer.class));
    }
    @Test void editingLegacyGuardianCreatesMissingPhoneAccount() {
        String id=ShortIds.next(jdbc,"guardian"),phone=phone();
        jdbc.update("INSERT INTO guardian(id,code,full_name,phone) VALUES(?,?,'Legacy synthetic',?)",id,"PL-"+id.substring(0,8),phone);
        guardians.update(id,parent(phone,null));
        assertNotNull(userId(id));assertEquals("PARENT",auth.currentUser(auth.authenticate(phone,"123456")).role());
    }
}

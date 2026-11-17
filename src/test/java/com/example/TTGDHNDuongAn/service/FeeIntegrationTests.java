package com.example.TTGDHNDuongAn.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.example.TTGDHNDuongAn.repository.ShortIds;
import com.example.TTGDHNDuongAn.security.AppUserPrincipal;
import com.example.TTGDHNDuongAn.service.FeeService.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(properties={"spring.datasource.url=${FEE_TEST_DB_URL}","SETUP_ADMIN_EMAIL=","SETUP_ADMIN_PASSWORD=",
    "spring.docker.compose.enabled=false","logging.level.root=WARN","debug=false"})
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named="FEE_TEST_DB_URL",matches=".+")
@Transactional
class FeeIntegrationTests {
    @Autowired FeeService fees;
    @Autowired StudentProfileService students;
    @Autowired GuardianProfileService guardians;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    final ObjectMapper json = new ObjectMapper();
    AppUserPrincipal admin, parent, other;
    String studentId, otherStudentId;
    String phone() { return "09"+String.format("%08d",Integer.toUnsignedLong(UUID.randomUUID().hashCode())%100000000); }
    AppUserPrincipal actor(String id,String role) { return new AppUserPrincipal(id,null,"Người dùng giả lập",role,"unused"); }
    StudentProfileService.ProfileInput child(String phone) {
        return new StudentProfileService.ProfileInput(null,"Học sinh học phí giả lập",LocalDate.of(2022,1,1),LocalDate.of(2024,1,1),null,null,
            new StudentProfileService.GuardianInput("Phụ huynh học phí giả lập",phone,null,null,"MOTHER"));
    }
    @BeforeEach void setup() {
        String id = ShortIds.next(jdbc,"app_user");
        jdbc.update("INSERT INTO app_user(id,email,password_hash,display_name,role,status) VALUES(?,?,?,'Admin học phí giả lập','ADMIN','ACTIVE')",
            id,UUID.randomUUID()+"@fees.invalid","unused");
        admin=actor(id,"ADMIN");
        var first=students.create(child(phone())); var second=students.create(child(phone()));
        studentId=first.id(); otherStudentId=second.id();
        parent=actor(jdbc.queryForObject("SELECT user_id FROM guardian WHERE id=?",String.class,first.guardian().id()),"PARENT");
        other=actor(jdbc.queryForObject("SELECT user_id FROM guardian WHERE id=?",String.class,second.guardian().id()),"PARENT");
    }
    CreateFee input(String student) { return new CreateFee(student,"Học phí giả lập",YearMonth.now().toString(),new BigDecimal("2500000"),null); }
    Fee create() { return fees.create(admin,input(studentId)); }
    @Test void newFeeIsPersistedAndOnlyVisibleToLinkedParentEvenWithoutAcademicPortalPublication() {
        Fee fee=create();
        assertEquals("ISSUED",jdbc.queryForObject("SELECT status FROM invoice WHERE id=?",String.class,fee.id()));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM invoice_line WHERE invoice_id=?",Integer.class,fee.id()));
        assertEquals(0,jdbc.queryForObject("SELECT can_view_portal FROM student_guardian WHERE student_id=?",Integer.class,studentId));
        assertEquals(new BigDecimal("2500000"),fee.amount());
        assertEquals(fee.id(),fees.list(parent).getFirst().id());
        assertTrue(fees.list(other).isEmpty());
        assertEquals(studentId,fees.students(parent).getFirst().id());
        assertFalse(fees.students(parent).stream().anyMatch(s->s.id().equals(otherStudentId)));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM payment WHERE invoice_id=?",Integer.class,fee.id()));
    }
    @Test void revokedFamilyLinkAndInactiveAccountLoseFinancialVisibility() {
        create();
        jdbc.update("UPDATE student_guardian SET revoked_at=UTC_TIMESTAMP(6) WHERE student_id=?",studentId);
        assertTrue(fees.list(parent).isEmpty()); assertTrue(fees.students(parent).isEmpty());
        jdbc.update("UPDATE student_guardian SET revoked_at=NULL WHERE student_id=?",studentId);
        jdbc.update("UPDATE app_user SET status='DISABLED' WHERE id=?",parent.id());
        assertTrue(fees.list(parent).isEmpty()); assertTrue(fees.students(parent).isEmpty());
    }
    @Test void unlinkedParentSeesEmptyLists() {
        create();
        var guardian=guardians.create(new GuardianProfileService.GuardianInput("Phụ huynh chưa có con giả lập",phone(),null,null));
        var unlinked=actor(jdbc.queryForObject("SELECT user_id FROM guardian WHERE id=?",String.class,guardian.id()),"PARENT");
        assertTrue(fees.list(unlinked).isEmpty()); assertTrue(fees.students(unlinked).isEmpty());
    }
    @Test void parentWithSiblingsSeesBothChildrenWithoutDuplicateInvoices() {
        var first=create();
        String parentPhone=jdbc.queryForObject("SELECT phone FROM app_user WHERE id=?",String.class,parent.id());
        var sibling=students.create(child(parentPhone));
        var second=fees.create(admin,input(sibling.id()));
        assertEquals(2,fees.students(parent).size());
        assertEquals(java.util.Set.of(first.id(),second.id()),fees.list(parent).stream().map(Fee::id).collect(java.util.stream.Collectors.toSet()));
        assertEquals(2,fees.list(parent).size()); assertTrue(fees.list(other).isEmpty());
    }
    @Test void paymentPersistsAndRepeatedRequestDoesNotDuplicatePayment() {
        var fee=create(); var paid=fees.pay(admin,fee.id()); fees.pay(admin,fee.id());
        assertEquals(0,paid.outstandingAmount().signum()); assertNotNull(paid.paidAt());
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM payment WHERE invoice_id=?",Integer.class,fee.id()));
        assertNotNull(fees.list(parent).getFirst().paidAt());
        assertEquals(paid.amount(),paid.paidAmount());
    }
    @Test void partiallyPaidInvoiceOnlyCollectsRemainingBalance() {
        var fee=create();
        jdbc.update("INSERT INTO payment(id,receipt_code,invoice_id,amount,method,paid_at,idempotency_key,received_by) VALUES(?,?,?,1000000,'CASH',UTC_TIMESTAMP(6),?,?)",
            ShortIds.next(jdbc,"payment"),"PT-"+UUID.randomUUID(),fee.id(),UUID.randomUUID().toString(),admin.id());
        var partial=fees.list(parent).getFirst(); assertNull(partial.paidAt());
        assertEquals(new BigDecimal("1500000"),partial.outstandingAmount());
        fees.pay(admin,fee.id());
        assertEquals(new BigDecimal("2500000"),jdbc.queryForObject("SELECT SUM(amount) FROM payment WHERE invoice_id=?",BigDecimal.class,fee.id()));
    }
    @Test void draftsAndVoidedInvoicesNeverAppearToParents() {
        var fee=create();
        jdbc.update("UPDATE invoice SET status='DRAFT' WHERE id=?",fee.id()); assertTrue(fees.list(parent).isEmpty());
        assertEquals(409,assertThrows(ResponseStatusException.class,()->fees.pay(admin,fee.id())).getStatusCode().value());
        jdbc.update("UPDATE invoice SET status='VOID',void_reason='Dữ liệu kiểm thử' WHERE id=?",fee.id()); assertTrue(fees.list(parent).isEmpty());
    }
    @Test void invalidAmountsMonthsAndUnknownStudentDoNotCreateInvoices() {
        int before=jdbc.queryForObject("SELECT COUNT(*) FROM invoice",Integer.class);
        for(String amount:new String[]{"0","-1","1.5","100000000000000"}) {
            assertEquals(400,assertThrows(ResponseStatusException.class,()->fees.create(admin,new CreateFee(studentId,"Học phí","2026-11",new BigDecimal(amount),null))).getStatusCode().value());
        }
        for(String month:new String[]{"2026-13","2026-1","0000-01"}) {
            assertEquals(400,assertThrows(ResponseStatusException.class,()->fees.create(admin,new CreateFee(studentId,"Học phí",month,BigDecimal.TEN,null))).getStatusCode().value());
        }
        assertEquals(404,assertThrows(ResponseStatusException.class,()->fees.create(admin,input("missing"))).getStatusCode().value());
        assertEquals(before,jdbc.queryForObject("SELECT COUNT(*) FROM invoice",Integer.class));
    }
    @Test void httpEnforcesAuthenticationRolesCsrfAndParentIsolation() throws Exception {
        var fee=create(); String body=json.writeValueAsString(input(studentId));
        mvc.perform(get("/api/fees")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/fees").with(user(actor(admin.id(),"TEACHER")))).andExpect(status().isForbidden());
        mvc.perform(get("/api/fees").with(user(parent))).andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(fee.id()));
        mvc.perform(get("/api/fees").with(user(other))).andExpect(status().isOk()).andExpect(content().json("[]"));
        mvc.perform(post("/api/fees").with(user(parent)).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden());
        mvc.perform(post("/api/fees/"+fee.id()+"/payment").with(user(parent)).with(csrf())).andExpect(status().isForbidden());
        mvc.perform(post("/api/fees").with(user(admin)).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden());
        mvc.perform(post("/api/fees").with(user(admin)).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk())
            .andExpect(jsonPath("$.studentId").value(studentId)).andExpect(jsonPath("$.amount").value(2500000));
    }
}

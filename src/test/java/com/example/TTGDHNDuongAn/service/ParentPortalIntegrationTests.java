package com.example.TTGDHNDuongAn.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.example.TTGDHNDuongAn.repository.ShortIds;
import com.example.TTGDHNDuongAn.security.AppUserPrincipal;
import java.time.*;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(properties={"spring.datasource.url=${PARENT_PORTAL_TEST_DB_URL}","SETUP_ADMIN_EMAIL=","SETUP_ADMIN_PASSWORD=",
    "spring.docker.compose.enabled=false","logging.level.root=WARN","debug=false"})
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named="PARENT_PORTAL_TEST_DB_URL",matches=".+")
@Transactional
class ParentPortalIntegrationTests {
    @Autowired ParentPortalService portal;
    @Autowired StudentProfileService students;
    @Autowired GuardianProfileService guardians;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    String phone, studentId, otherStudentId, teacherId, classId, sessionId;
    AppUserPrincipal parent, other;
    String phone() { return "09"+String.format("%08d",Integer.toUnsignedLong(UUID.randomUUID().hashCode())%100000000); }
    AppUserPrincipal actor(String id,String role) { return new AppUserPrincipal(id,null,"Phụ huynh giả lập",role,"unused"); }
    StudentProfileService.ProfileInput child(String name,String phone) {
        return new StudentProfileService.ProfileInput(null,name,LocalDate.of(2022,1,1),LocalDate.of(2024,1,1),null,"PRIVATE_INTERNAL_NOTE",
            new StudentProfileService.GuardianInput("Phụ huynh cổng thông tin giả lập",phone,null,null,"MOTHER"));
    }
    String userId(String guardian) {return jdbc.queryForObject("SELECT user_id FROM guardian WHERE id=?",String.class,guardian);}
    void enroll(String student,LocalDate start) {
        jdbc.update("INSERT INTO enrollment(id,class_id,student_id,started_on,status) VALUES(?,?,?,?,'ENROLLED')",
            ShortIds.next(jdbc,"enrollment"),classId,student,start);
    }
    @BeforeEach void setup() {
        phone=phone(); var first=students.create(child("Con phụ huynh A giả lập",phone));
        var second=students.create(child("Con phụ huynh B riêng tư giả lập",phone()));
        studentId=first.id(); otherStudentId=second.id();
        parent=actor(userId(first.guardian().id()),"PARENT"); other=actor(userId(second.guardian().id()),"PARENT");
        teacherId=ShortIds.next(jdbc,"staff");
        jdbc.update("INSERT INTO staff(id,code,full_name,joined_on) VALUES(?,?,'Giáo viên cổng phụ huynh giả lập','2024-01-01')",teacherId,"GV-"+UUID.randomUUID().toString().substring(0,20));
        classId=ShortIds.next(jdbc,"learning_class");
        jdbc.update("INSERT INTO learning_class(id,code,name,class_type,teacher_id,capacity,starts_on,status) VALUES(?,?,'Nhóm giả lập','GROUP',?,10,'2024-01-01','ACTIVE')",
            classId,"LP-"+UUID.randomUUID().toString().substring(0,20),teacherId);
        enroll(studentId,LocalDate.of(2024,1,1)); enroll(otherStudentId,LocalDate.of(2024,1,1));
        sessionId=ShortIds.next(jdbc,"class_session");
        LocalDateTime start=LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")).atTime(9,0).minusHours(7);
        jdbc.update("INSERT INTO class_session(id,class_id,teacher_id,starts_at,ends_at,created_by) VALUES(?,?,?,?,?,?)",
            sessionId,classId,teacherId,start,start.plusHours(1),parent.id());
        for(String student:new String[]{studentId,otherStudentId})
            jdbc.update("INSERT INTO student_attendance(id,session_id,student_id,status,recorded_by) VALUES(?,?,?,'PRESENT',?)",
                ShortIds.next(jdbc,"student_attendance"),sessionId,student,parent.id());
    }
    @Test void ownProfileUsesAccountLinksAndOnlyReturnsOwnedClassMembersAttendanceAndTeacherNames() {
        var data=portal.get(parent);
        assertEquals("READY",data.linkStatus()); assertEquals(parent.id(),userId(data.guardian().id()));
        assertEquals(1,data.children().size()); assertEquals(studentId,data.children().getFirst().id());
        assertEquals(1,data.memberships().size()); assertEquals(studentId,data.memberships().getFirst().studentId());
        assertEquals(1,data.sessions().size()); assertEquals(sessionId,data.sessions().getFirst().id());
        assertEquals(teacherId,data.teachers().getFirst().id());
        assertEquals(1,data.attendance().size()); assertEquals(studentId,data.attendance().getFirst().studentId());
        String payload=new ObjectMapper().writeValueAsString(data);
        assertFalse(payload.contains(otherStudentId)); assertFalse(payload.contains("PRIVATE_INTERNAL_NOTE"));
        assertFalse(payload.contains("password")); assertFalse(payload.contains("salary"));
        assertEquals(0,jdbc.queryForObject("SELECT can_view_portal FROM student_guardian WHERE student_id=?",Integer.class,studentId));
    }
    @Test void siblingProfilesAreBothAvailableAndDoNotDependOnPrimaryGuardian() {
        var sibling=students.create(child("Em ruột giả lập",phone));
        jdbc.update("UPDATE student_guardian SET is_primary=0 WHERE student_id=?",sibling.id());
        var data=portal.get(parent);
        assertEquals(2,data.children().size());
        assertTrue(data.children().stream().anyMatch(s->s.id().equals(sibling.id())));
        assertFalse(portal.get(other).children().stream().anyMatch(s->s.id().equals(sibling.id())));
    }
    @Test void revokedLinkImmediatelyRemovesChildClassesTeachersAndAttendance() {
        jdbc.update("UPDATE student_guardian SET revoked_at=UTC_TIMESTAMP(6) WHERE student_id=?",studentId);
        var data=portal.get(parent); assertEquals("NO_LINKED_STUDENT",data.linkStatus());
        assertTrue(data.children().isEmpty()); assertTrue(data.memberships().isEmpty());
        assertTrue(data.sessions().isEmpty()); assertTrue(data.teachers().isEmpty()); assertTrue(data.attendance().isEmpty());
        assertEquals(otherStudentId,portal.get(other).children().getFirst().id());
    }
    @Test void noGuardianAndNoChildAreDistinctEmptyStates() {
        String id=ShortIds.next(jdbc,"app_user");
        jdbc.update("INSERT INTO app_user(id,email,password_hash,display_name,role) VALUES(?,?,'unused','Tài khoản chưa liên kết giả lập','PARENT')",id,UUID.randomUUID()+"@parent-portal.invalid");
        assertEquals("NO_GUARDIAN_PROFILE",portal.get(actor(id,"PARENT")).linkStatus());
        var guardian=guardians.create(new GuardianProfileService.GuardianInput("Phụ huynh chưa liên kết giả lập",phone(),null,null));
        assertEquals("NO_LINKED_STUDENT",portal.get(actor(userId(guardian.id()),"PARENT")).linkStatus());
    }
    @Test void futureEndedAndCancelledEnrollmentsNeverAppearAsCurrentClasses() {
        jdbc.update("UPDATE enrollment SET started_on=DATE(UTC_TIMESTAMP()+INTERVAL 7 HOUR)+INTERVAL 1 DAY WHERE student_id=?",studentId);
        var data=portal.get(parent); assertEquals(1,data.children().size());
        assertTrue(data.memberships().isEmpty()); assertTrue(data.sessions().isEmpty()); assertTrue(data.teachers().isEmpty());
        jdbc.update("UPDATE enrollment SET started_on='2024-01-01',ended_on=DATE(UTC_TIMESTAMP()+INTERVAL 7 HOUR),status='ENDED' WHERE student_id=?",studentId);
        assertTrue(portal.get(parent).memberships().isEmpty());
        jdbc.update("UPDATE enrollment SET status='CANCELLED' WHERE student_id=?",studentId);
        assertTrue(portal.get(parent).sessions().isEmpty());
    }
    @Test void cancelledSessionsAndUnpublishedOrOtherRecipientsNotificationsAreHidden() {
        jdbc.update("UPDATE class_session SET status='CANCELLED' WHERE id=?",sessionId);
        assertTrue(portal.get(parent).sessions().isEmpty()); assertTrue(portal.get(parent).attendance().isEmpty());
        for(String status:new String[]{"DRAFT","PUBLISHED"}) {
            String id=ShortIds.next(jdbc,"notification");
            jdbc.update("INSERT INTO notification(id,title,body,status,created_by,published_at) VALUES(?,'Thông báo giả lập','Nội dung giả lập',?,?,CASE WHEN ?='PUBLISHED' THEN UTC_TIMESTAMP(6) ELSE NULL END)",id,status,parent.id(),status);
            jdbc.update("INSERT INTO notification_recipient(notification_id,user_id) VALUES(?,?)",id,status.equals("DRAFT")?parent.id():other.id());
        }
        assertTrue(portal.get(parent).notices().isEmpty()); assertEquals(1,portal.get(other).notices().size());
    }
    @Test void disabledAccountAndWrongRolesCannotReadEvenWithAnExistingSession() throws Exception {
        for(String status:new String[]{"LOCKED","DISABLED"}) {
            jdbc.update("UPDATE app_user SET status=? WHERE id=?",status,parent.id());
            assertEquals(403,assertThrows(ResponseStatusException.class,()->portal.get(parent)).getStatusCode().value());
            mvc.perform(get("/api/parent/portal").with(user(parent))).andExpect(status().isForbidden());
        }
        mvc.perform(get("/api/parent/portal")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/parent/portal").with(user(actor(parent.id(),"ADMIN")))).andExpect(status().isForbidden());
        mvc.perform(get("/api/parent/portal").with(user(actor(parent.id(),"TEACHER")))).andExpect(status().isForbidden());
    }
    @Test void httpIgnoresForgedGuardianAndStudentParametersAndDoesNotChangeData() throws Exception {
        mvc.perform(get("/api/parent/portal").param("userId",other.id()).param("studentId",otherStudentId).with(user(parent)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.children.length()").value(1))
            .andExpect(jsonPath("$.children[0].id").value(studentId)).andExpect(jsonPath("$.attendance[0].studentId").value(studentId));
        mvc.perform(post("/api/parent/portal").with(user(parent)).with(csrf())).andExpect(status().isForbidden());
        assertEquals("ACTIVE",jdbc.queryForObject("SELECT status FROM student WHERE id=?",String.class,studentId));
        assertEquals(0,jdbc.queryForObject("SELECT can_view_portal FROM student_guardian WHERE student_id=?",Integer.class,studentId));
    }
}

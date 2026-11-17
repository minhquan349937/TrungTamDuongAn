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

@SpringBootTest(properties={"spring.datasource.url=${TEACHER_PORTAL_TEST_DB_URL}","SETUP_ADMIN_EMAIL=","SETUP_ADMIN_PASSWORD=",
    "spring.docker.compose.enabled=false","logging.level.root=WARN","debug=false"})
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named="TEACHER_PORTAL_TEST_DB_URL",matches=".+")
@Transactional
class TeacherPortalIntegrationTests {
    @Autowired TeacherPortalService service;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @Autowired ClassManagementService classes;
    AppUserPrincipal teacher,other;
    String staff,otherStaff,group,emptyGroup,individual,otherGroup,student,otherStudent,expired;
    LocalDate today=LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh"));
    String id(String table) {return ShortIds.next(jdbc,table);}
    String code() {return "TP-"+UUID.randomUUID().toString().substring(0,8);}
    AppUserPrincipal account(String role) {
        String id=id("app_user"),email=UUID.randomUUID()+"@teacher-test.invalid";
        jdbc.update("INSERT INTO app_user(id,email,password_hash,display_name,role,status) VALUES(?,?,'unused','Teacher synthetic',?,'ACTIVE')",id,email,role);
        return new AppUserPrincipal(id,email,"Teacher synthetic",role,"unused");
    }
    String profile(AppUserPrincipal user) {
        String id=id("staff");jdbc.update("INSERT INTO staff(id,code,user_id,full_name,joined_on) VALUES(?,?,?,'Teacher synthetic',?)",id,code(),user.id(),today.minusYears(1));return id;
    }
    String child() {
        String id=id("student");jdbc.update("INSERT INTO student(id,code,full_name,birth_date,admitted_on,notes) VALUES(?,?,'Assigned synthetic child',?,?,'PRIVATE_INTERNAL_NOTE')",id,code(),today.minusYears(5),today.minusYears(1));return id;
    }
    String cls(String staff,String type) {
        String id=id("learning_class");jdbc.update("INSERT INTO learning_class(id,code,name,class_type,teacher_id,capacity,starts_on,status) VALUES(?,?,'Assigned synthetic class',?,?,?,?,'ACTIVE')",id,code(),type,staff,type.equals("INDIVIDUAL")?1:10,today.minusDays(3));return id;
    }
    void enroll(String cls,String student) {
        jdbc.update("INSERT INTO enrollment(id,class_id,student_id,started_on,status) VALUES(?,?,?,?,'ENROLLED')",id("enrollment"),cls,student,today.minusDays(2));
    }
    @BeforeEach void setup() {
        teacher=account("TEACHER");other=account("TEACHER");staff=profile(teacher);otherStaff=profile(other);
        group=cls(staff,"GROUP");emptyGroup=cls(staff,"GROUP");individual=cls(staff,"INDIVIDUAL");otherGroup=cls(otherStaff,"GROUP");
        student=child();otherStudent=child();expired=child();
        enroll(group,student);enroll(individual,student);enroll(otherGroup,otherStudent);enroll(group,expired);
        jdbc.update("UPDATE enrollment SET ended_on=?,status='ENDED' WHERE class_id=? AND student_id=?",today,group,expired);
    }
    @Test void eachAccountSeesOnlyItsAssignedClassesAndDeduplicatedStudents() {
        var result=service.get(teacher);assertEquals(staff,result.teacher().id());
        assertEquals(3,result.classes().size());assertTrue(result.classes().stream().anyMatch(c->c.id().equals(emptyGroup)));
        assertEquals(1,result.students().size());assertEquals(student,result.students().getFirst().id());
        assertEquals(2,result.memberships().size());
        var otherResult=service.get(other);assertEquals(1,otherResult.classes().size());assertEquals(otherGroup,otherResult.classes().getFirst().id());
        assertEquals(otherStudent,otherResult.students().getFirst().id());
    }
    @Test void reassigningClassImmediatelyRemovesItFromPreviousTeacher() {
        jdbc.update("UPDATE learning_class SET teacher_id=? WHERE id=?",otherStaff,group);
        assertTrue(service.get(teacher).classes().stream().noneMatch(c->c.id().equals(group)));
        assertTrue(service.get(other).students().stream().anyMatch(s->s.id().equals(student)));
        jdbc.update("UPDATE learning_class SET status='CLOSED' WHERE id=?",individual);
        assertTrue(service.get(teacher).students().isEmpty());
    }
    @Test void futureAndEndedAssignmentsAreExcluded() {
        jdbc.update("UPDATE learning_class SET starts_on=? WHERE id=?",today.plusDays(1),group);
        jdbc.update("UPDATE learning_class SET ends_on=? WHERE id=?",today,individual);
        var result=service.get(teacher);assertTrue(result.students().isEmpty());
        assertEquals(1,result.classes().size());assertEquals(emptyGroup,result.classes().getFirst().id());
    }
    @Test void missingOrInactiveStaffReturnsEmptyAndDisabledAccountIsDenied() {
        var unlinked=account("TEACHER");assertEquals("NO_ACTIVE_STAFF_PROFILE",service.get(unlinked).linkStatus());
        jdbc.update("UPDATE staff SET status='TERMINATED' WHERE id=?",staff);
        assertTrue(service.get(teacher).students().isEmpty());assertTrue(service.get(teacher).classes().isEmpty());
        jdbc.update("UPDATE app_user SET status='DISABLED' WHERE id=?",teacher.id());
        assertEquals(403,assertThrows(ResponseStatusException.class,()->service.get(teacher)).getStatusCode().value());
    }
    @Test void apiScopesFromSessionAndNeverReturnsPrivateNotesOrSalary() throws Exception {
        mvc.perform(get("/api/teacher/portal")).andExpect(status().isUnauthorized());
        for(String role:new String[]{"PARENT","ADMIN"}) mvc.perform(get("/api/teacher/portal").with(user(account(role)))).andExpect(status().isForbidden());
        String body=mvc.perform(get("/api/teacher/portal").param("teacherId",otherStaff).with(user(teacher)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.teacher.id").value(staff))
            .andExpect(jsonPath("$.students.length()").value(1)).andReturn().getResponse().getContentAsString();
        assertFalse(body.contains(otherStudent));assertFalse(body.contains(otherGroup));assertFalse(body.contains("PRIVATE_INTERNAL_NOTE"));assertFalse(body.contains("salary"));
    }
    @Test void sessionsAndAttendanceAreLimitedToOwnClassesAndCurrentRoster() {
        String session=id("class_session");
        jdbc.update("INSERT INTO class_session(id,class_id,teacher_id,starts_at,ends_at,created_by) VALUES(?,?,?,?,?,?)",session,group,staff,today.atTime(1,0),today.atTime(2,0),teacher.id());
        jdbc.update("INSERT INTO student_attendance(id,session_id,student_id,status,recorded_by) VALUES(?,?,?,'PRESENT',?)",id("student_attendance"),session,student,teacher.id());
        var result=service.get(teacher);assertEquals(1,result.sessions().size());assertEquals(1,result.attendance().size());
        assertTrue(service.get(other).sessions().isEmpty());assertTrue(service.get(other).attendance().isEmpty());
    }
    @Test void adminClassChangesPersistAndImmediatelyUpdateTeacherAssignments() {
        var created=classes.save(null,new ClassManagementService.Input("New persisted group","GROUP",staff,java.util.List.of(otherStudent),8,"ACTIVE"));
        assertTrue(service.get(teacher).classes().stream().anyMatch(c->c.id().equals(created.id())));
        assertTrue(service.get(teacher).students().stream().anyMatch(s->s.id().equals(otherStudent)));
        assertTrue(classes.list("GROUP").stream().anyMatch(c->c.id().equals(created.id())));
        // Remove and re-add a same-day enrollment without violating its date or unique constraints.
        classes.save(created.id(),new ClassManagementService.Input("New persisted group","GROUP",staff,java.util.List.of(),8,"ACTIVE"));
        classes.save(created.id(),new ClassManagementService.Input("New persisted group","GROUP",otherStaff,java.util.List.of(otherStudent),8,"ACTIVE"));
        assertTrue(service.get(teacher).classes().stream().noneMatch(c->c.id().equals(created.id())));
        assertTrue(service.get(other).classes().stream().anyMatch(c->c.id().equals(created.id())));
        classes.close(created.id());
        assertTrue(service.get(other).classes().stream().noneMatch(c->c.id().equals(created.id())));
        assertTrue(classes.list("GROUP").stream().noneMatch(c->c.id().equals(created.id())));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM enrollment WHERE class_id=?",Integer.class,created.id()));
    }
    @Test void invalidClassInputCannotChangeExistingAssignment() {
        assertEquals(400,assertThrows(ResponseStatusException.class,()->classes.save(group,new ClassManagementService.Input("Group","GROUP",otherStaff,java.util.List.of(student,otherStudent),1,"ACTIVE"))).getStatusCode().value());
        assertEquals(staff,jdbc.queryForObject("SELECT teacher_id FROM learning_class WHERE id=?",String.class,group));
    }
    @Test void adminClassApiRequiresAdminAndCsrfAndPersistsRoster() throws Exception {
        var admin=account("ADMIN");
        String body="{\"id\":\"local-class\",\"name\":\"API persisted group\",\"type\":\"GROUP\",\"teacherId\":\""+staff+"\",\"studentIds\":[\""+student+"\"],\"capacity\":8,\"status\":\"ACTIVE\"}";
        mvc.perform(get("/api/admin/classes").param("type","GROUP").with(user(teacher))).andExpect(status().isForbidden());
        mvc.perform(post("/api/admin/classes").with(user(teacher)).with(csrf()).contentType(org.springframework.http.MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden());
        mvc.perform(post("/api/admin/classes").with(user(admin)).contentType(org.springframework.http.MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden());
        String response=mvc.perform(post("/api/admin/classes").with(user(admin)).with(csrf()).contentType(org.springframework.http.MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isOk()).andExpect(jsonPath("$.studentIds[0]").value(student)).andReturn().getResponse().getContentAsString();
        String createdId=new tools.jackson.databind.ObjectMapper().readTree(response).path("id").asText();
        assertTrue(classes.list("GROUP").stream().anyMatch(c->c.id().equals(createdId) && c.studentIds().contains(student)));
        mvc.perform(delete("/api/admin/classes/"+createdId).with(user(admin)).with(csrf())).andExpect(status().isNoContent());
        assertTrue(service.get(teacher).classes().stream().noneMatch(c->c.id().equals(createdId)));
    }
}

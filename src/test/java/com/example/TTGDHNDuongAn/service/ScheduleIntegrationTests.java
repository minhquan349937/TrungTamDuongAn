package com.example.TTGDHNDuongAn.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.example.TTGDHNDuongAn.repository.ShortIds;
import com.example.TTGDHNDuongAn.security.AppUserPrincipal;
import com.example.TTGDHNDuongAn.service.ScheduleService.*;
import java.time.*;
import java.util.*;
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

@SpringBootTest(properties={"spring.datasource.url=${SCHEDULE_TEST_DB_URL}","SETUP_ADMIN_EMAIL=","SETUP_ADMIN_PASSWORD=",
    "spring.docker.compose.enabled=false","logging.level.root=WARN","debug=false"})
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named="SCHEDULE_TEST_DB_URL",matches=".+")
@Transactional
class ScheduleIntegrationTests {
    @Autowired ScheduleService service;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    AppUserPrincipal admin,teacher,otherTeacher,parent;
    String staff,otherStaff,cls,otherClass,sameTeacherClass,sharedClass,student,otherStudent;
    final LocalDate day=LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")).plusYears(3);
    final String room="Schedule test "+UUID.randomUUID();
    final ObjectMapper json=new ObjectMapper();
    String id(String table) {return ShortIds.next(jdbc,table);}
    String code() {return "SC-"+UUID.randomUUID().toString().substring(0,8);}
    AppUserPrincipal account(String role) {
        String id=id("app_user"),email=UUID.randomUUID()+"@schedule-test.invalid";
        jdbc.update("INSERT INTO app_user(id,email,password_hash,display_name,role,status) VALUES(?,?,'unused','Schedule test',?,'ACTIVE')",id,email,role);
        return new AppUserPrincipal(id,email,"Schedule test",role,"unused");
    }
    String staff(AppUserPrincipal actor) {
        String id=id("staff");jdbc.update("INSERT INTO staff(id,code,user_id,full_name,joined_on) VALUES(?,?,?,'Schedule test teacher','2024-01-01')",id,code(),actor.id());return id;
    }
    String cls(String teacher,String type) {
        String id=id("learning_class");jdbc.update("INSERT INTO learning_class(id,code,name,class_type,teacher_id,capacity,starts_on,status) VALUES(?,?,'Schedule test class',?,?,?,?,'ACTIVE')",id,code(),type,teacher,type.equals("INDIVIDUAL")?1:10,day.minusDays(1));return id;
    }
    String student() {
        String id=id("student");jdbc.update("INSERT INTO student(id,code,full_name,birth_date,admitted_on) VALUES(?,?,'Schedule test child','2021-01-01','2024-01-01')",id,code());return id;
    }
    void enroll(String cls,String student) {jdbc.update("INSERT INTO enrollment(id,class_id,student_id,started_on) VALUES(?,?,?,?)",id("enrollment"),cls,student,day.minusDays(1));}
    Input input(String cls,int start,int end,String room) {return new Input(cls,day,LocalTime.of(start,0),LocalTime.of(end,0),room,null);}
    @BeforeEach void setup() {
        admin=account("ADMIN");teacher=account("TEACHER");otherTeacher=account("TEACHER");parent=account("PARENT");
        staff=staff(teacher);otherStaff=staff(otherTeacher);cls=cls(staff,"INDIVIDUAL");otherClass=cls(otherStaff,"GROUP");sameTeacherClass=cls(staff,"GROUP");sharedClass=cls(otherStaff,"DAYCARE");
        student=student();otherStudent=student();enroll(cls,student);enroll(otherClass,otherStudent);enroll(sharedClass,student);
        String guardian=id("guardian");jdbc.update("INSERT INTO guardian(id,code,user_id,full_name) VALUES(?,?,?,'Schedule test guardian')",guardian,code(),parent.id());
        jdbc.update("INSERT INTO student_guardian(student_id,guardian_id,relationship,is_primary,can_view_portal) VALUES(?,?,'GUARDIAN',0,0)",student,guardian);
    }
    @Test void catalogIncludesPersistedClassesWithNoLessonsAndSaveUsesUtcThenReadReturnsVietnamTime() {
        var before=service.get(day,day,admin);assertTrue(before.classes().stream().anyMatch(c->c.id().equals(cls)));assertTrue(before.sessions().isEmpty());
        String lesson=service.save(null,input(cls,8,9,room),admin);
        var saved=jdbc.queryForObject("SELECT starts_at,ends_at FROM class_session WHERE id=?",(rs,n)->List.of(rs.getObject(1,LocalDateTime.class),rs.getObject(2,LocalDateTime.class)),lesson);
        assertEquals(day.atTime(1,0),saved.get(0));assertEquals(day.atTime(2,0),saved.get(1));
        var read=service.get(day,day,teacher).sessions().getFirst();assertEquals(LocalTime.of(8,0),read.start());assertEquals(day,read.date());assertEquals(List.of(student),read.studentIds());
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM audit_event WHERE entity_id=? AND action='CREATE_SESSION'",Integer.class,lesson));
    }
    @Test void checksTeacherClassRoomAndStudentOverlapAndAllowsAdjacentSessions() {
        service.save(null,input(cls,8,9,room),admin);
        for(Input input:List.of(input(cls,8,10,room+"2"),input(sameTeacherClass,8,10,room+"2"),input(otherClass,8,10,room),input(sharedClass,8,10,room+"2")))
            assertEquals(409,assertThrows(ResponseStatusException.class,()->service.save(null,input,admin)).getStatusCode().value());
        assertNotNull(service.save(null,input(cls,9,10,room),admin));
    }
    @Test void reschedulingUpdatesSameRowAndRejectsClassChanges() {
        String lesson=service.save(null,input(cls,8,9,room),admin);
        service.save(lesson,input(cls,10,11,room+"2"),admin);
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM class_session WHERE id=?",Integer.class,lesson));
        assertEquals(LocalTime.of(10,0),service.get(day,day,teacher).sessions().getFirst().start());
        assertEquals(400,assertThrows(ResponseStatusException.class,()->service.save(lesson,input(otherClass,10,11,room),admin)).getStatusCode().value());
    }
    @Test void teacherAndParentCannotCreateAndEachReadIsScopedToTheirOwnSessionsAndChildren() {
        service.save(null,input(cls,8,9,room),admin);service.save(null,input(otherClass,10,11,room),admin);
        for(var actor:List.of(teacher,parent)) assertEquals(403,assertThrows(ResponseStatusException.class,()->service.save(null,input(cls,12,13,room),actor)).getStatusCode().value());
        assertEquals(1,service.get(day,day,teacher).sessions().size());assertEquals(cls,service.get(day,day,teacher).sessions().getFirst().classId());
        var family=service.get(day,day,parent);assertEquals(1,family.sessions().size());assertEquals(List.of(student),family.sessions().getFirst().studentIds());assertTrue(family.students().stream().noneMatch(s->s.id().equals(otherStudent)));
    }
    @Test void groupParentSeesOnlyLinkedChildNotClassmatesOrTheirAttendance() {
        enroll(otherClass,student);String lesson=service.save(null,input(otherClass,8,9,room),admin);
        service.attendance(lesson,new AttendanceInput(Map.of(student,"PRESENT",otherStudent,"ABSENT")),otherTeacher);
        var family=service.get(day,day,parent);assertEquals(List.of(student),family.sessions().getFirst().studentIds());assertEquals(Map.of(student,"PRESENT"),family.sessions().getFirst().attendance());assertEquals(1,family.students().size());
    }
    @Test void attendancePersistsCanBeCorrectedAndRejectsForeignStudentsAndOtherTeachers() {
        String lesson=service.save(null,input(cls,8,9,room),admin);
        assertEquals(403,assertThrows(ResponseStatusException.class,()->service.attendance(lesson,new AttendanceInput(Map.of(student,"PRESENT")),otherTeacher)).getStatusCode().value());
        assertEquals(400,assertThrows(ResponseStatusException.class,()->service.attendance(lesson,new AttendanceInput(Map.of(otherStudent,"PRESENT")),teacher)).getStatusCode().value());
        service.attendance(lesson,new AttendanceInput(Map.of(student,"LATE")),teacher);
        assertEquals("LATE",service.get(day,day,teacher).sessions().getFirst().attendance().get(student));
        service.attendance(lesson,new AttendanceInput(Map.of(student,"PRESENT")),admin);
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM student_attendance WHERE session_id=?",Integer.class,lesson));
        assertEquals(409,assertThrows(ResponseStatusException.class,()->service.save(lesson,input(cls,9,10,room),admin)).getStatusCode().value());
    }
    @Test void rejectsInvalidTimesDateOutsideClassAndInactiveTeacher() {
        assertEquals(400,assertThrows(ResponseStatusException.class,()->service.save(null,input(cls,9,8,room),admin)).getStatusCode().value());
        assertEquals(400,assertThrows(ResponseStatusException.class,()->service.save(null,new Input(cls,day.minusDays(2),LocalTime.of(8,0),LocalTime.of(9,0),room,null),admin)).getStatusCode().value());
        jdbc.update("UPDATE app_user SET status='DISABLED' WHERE id=?",teacher.id());
        assertEquals(400,assertThrows(ResponseStatusException.class,()->service.save(null,input(cls,8,9,room),admin)).getStatusCode().value());
    }
    @Test void endedEnrollmentRemainsInHistoricalRosterButIsNotIncludedOnEndDate() {
        String lesson=service.save(null,input(cls,8,9,room),admin);
        jdbc.update("UPDATE enrollment SET status='ENDED',ended_on=? WHERE class_id=? AND student_id=?",day.plusDays(1),cls,student);
        assertEquals(List.of(student),service.get(day,day,admin).sessions().stream().filter(s->s.id().equals(lesson)).findFirst().orElseThrow().studentIds());
        jdbc.update("UPDATE enrollment SET ended_on=? WHERE class_id=? AND student_id=?",day,cls,student);
        assertTrue(service.get(day,day,admin).sessions().stream().filter(s->s.id().equals(lesson)).findFirst().orElseThrow().studentIds().isEmpty());
    }
    @Test void endpointsEnforceSessionRoleCsrfAndSaveRealRecords() throws Exception {
        String body=json.writeValueAsString(input(cls,8,9,room));
        mvc.perform(get("/api/schedule").param("from",day.toString()).param("to",day.toString())).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/schedule/sessions").with(user(teacher)).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden());
        mvc.perform(post("/api/schedule/sessions").with(user(admin)).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden());
        String response=mvc.perform(post("/api/schedule/sessions").with(user(admin)).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String lesson=json.readTree(response).path("id").asText();assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM class_session WHERE id=?",Integer.class,lesson));
        mvc.perform(put("/api/schedule/sessions/"+lesson+"/attendance").with(user(teacher)).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(new AttendanceInput(Map.of(student,"PRESENT"))))).andExpect(status().isNoContent());
        mvc.perform(get("/api/schedule").with(user(parent)).param("from",day.toString()).param("to",day.toString())).andExpect(status().isOk()).andExpect(jsonPath("$.sessions[0].attendance."+student).value("PRESENT"));
    }
}

package com.example.TTGDHNDuongAn.catalog;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.example.TTGDHNDuongAn.dto.IndividualReportDtos.Edit;
import com.example.TTGDHNDuongAn.repository.ShortIds;
import com.example.TTGDHNDuongAn.security.AppUserPrincipal;
import com.example.TTGDHNDuongAn.service.*;
import java.math.BigDecimal;
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

@SpringBootTest(properties={"spring.datasource.url=${INDIVIDUAL_REPORT_TEST_DB_URL}","SETUP_ADMIN_EMAIL=","SETUP_ADMIN_PASSWORD=",
    "spring.docker.compose.enabled=false","logging.level.root=WARN","debug=false"})
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named="INDIVIDUAL_REPORT_TEST_DB_URL",matches=".+")
@Transactional
class IndividualReportMysqlTests {
    @Autowired JdbcTemplate jdbc;
    @Autowired IndividualReportService reports;
    @Autowired IndividualAssignmentService assignments;
    @Autowired PlanEvaluationService evaluations;
    @Autowired MockMvc mvc;
    String student,plan,first,second,goal;
    AppUserPrincipal admin,teacher,other;
    final ObjectMapper json=new ObjectMapper();
    String id(String table) {return ShortIds.next(jdbc,table);}
    AppUserPrincipal account(String role) {
        String id=id("app_user"),email="report-"+id+"@test.invalid";
        jdbc.update("INSERT INTO app_user(id,email,password_hash,display_name,role) VALUES(?,?,'unused','Report synthetic user',?)",id,email,role);
        return new AppUserPrincipal(id,email,"Report synthetic user",role,"unused");
    }
    String newStudent() {
        String id=id("student");jdbc.update("INSERT INTO student(id,code,full_name,birth_date,admitted_on) VALUES(?,?,'Report synthetic child','2022-01-01','2023-01-01')",id,"IR-"+id.substring(0,8));return id;
    }
    String report(LocalDateTime when,String notes) {
        String id=id("plan_evaluations");
        jdbc.update("INSERT INTO plan_evaluations(id,learning_plan_id,evaluation_notes,evaluated_by,evaluated_at,updated_at) VALUES(?,?,?,?,?,?)",id,plan,notes,admin.id(),when,when);return id;
    }
    @BeforeEach void setup() {
        admin=account("ADMIN");teacher=account("TEACHER");other=account("TEACHER");student=newStudent();plan=id("learning_plans");
        jdbc.update("INSERT INTO learning_plans(id,student_id,name,start_date,end_date,created_by,status) VALUES(?,?,'Report synthetic plan','2026-09-01','2026-10-31',?,'ACTIVE')",plan,student,admin.id());
        String target=jdbc.queryForObject("SELECT id FROM goals WHERE is_active=1 ORDER BY id LIMIT 1",String.class);
        goal=assign(target,LocalDateTime.of(2026,9,1,0,0),null);
        evaluation(goal,1,"50",LocalDateTime.of(2026,10,10,0,0));
        first=report(LocalDateTime.of(2026,10,15,0,0),"Nhận xét cũ cần giữ nguyên.");
        evaluation(goal,2,"90",LocalDateTime.of(2026,10,20,0,0));
        second=report(LocalDateTime.of(2026,10,25,0,0),"I. Tiến bộ\nA\nII. Khó khăn cần hỗ trợ\nB\nIII. Định hướng hỗ trợ\nC");
    }
    String assign(String target,LocalDateTime when,String supersedes) {
        String id=id("plan_goals");
        jdbc.update("INSERT INTO plan_goals(id,plan_id,goal_id,goal_snapshot,criteria_snapshot,assigned_by,assigned_at,supersedes_id) VALUES(?,?,?,JSON_OBJECT(),JSON_OBJECT(),?,?,?)",id,plan,target,admin.id(),when,supersedes);return id;
    }
    void evaluation(String goal,int attempt,String percent,LocalDateTime when) {
        jdbc.update("INSERT INTO goal_evaluations(id,plan_goal_id,attempt_no,achievement_percent,evaluated_by,evaluated_at,created_at,updated_at) VALUES(?,?,?,?,?,?,?,?)",
            id("goal_evaluations"),goal,attempt,new BigDecimal(percent),admin.id(),when,when,when);
    }
    void assignTeacher(AppUserPrincipal account) {
        String staff=id("staff");jdbc.update("INSERT INTO staff(id,code,user_id,full_name,joined_on) VALUES(?,?,?,'Report synthetic staff','2023-01-01')",staff,"IR-"+staff.substring(0,8),account.id());
        assignments.create(new IndividualAssignmentService.Create(student,staff));
    }
    @Test void sameMonthReportsRemainDistinctAndStudentsWithoutReportsAreExcluded() {
        var items=reports.reports(student,admin);assertEquals(2,items.size());assertNotEquals(items.get(0).id(),items.get(1).id());
        assertEquals(LocalDate.of(2026,9,1),items.getFirst().planStartDate());
        assertEquals(2,reports.students("IR-"+student.substring(0,8),admin).getFirst().reportCount());
        String empty=newStudent();assertTrue(reports.reports(empty,admin).isEmpty());
        assertTrue(reports.students("",admin).stream().noneMatch(s->s.id().equals(empty)));
    }
    @Test void reportTimestampsUseUtcWithoutShiftingWhenJvmUsesVietnamTime() {
        jdbc.update("UPDATE learning_plans SET end_date=NULL WHERE id=?",plan);
        LocalDateTime before=jdbc.queryForObject("SELECT UTC_TIMESTAMP(6)",(rs,n)->rs.getTimestamp(1).toLocalDateTime());
        var created=evaluations.create(plan,new com.example.TTGDHNDuongAn.dto.PlanEvaluationDtos.Input("Timezone regression"),admin);
        LocalDateTime after=jdbc.queryForObject("SELECT UTC_TIMESTAMP(6)",(rs,n)->rs.getTimestamp(1).toLocalDateTime());
        assertFalse(created.evaluatedAt().isBefore(before.minusSeconds(1)),"Creation must not shift UTC backward by the JVM offset");
        assertFalse(created.evaluatedAt().isAfter(after.plusSeconds(1)));
        assertFalse(reports.detail(created.id(),admin).goals().isEmpty());
        var updated=evaluations.update(created.id(),new com.example.TTGDHNDuongAn.dto.PlanEvaluationDtos.Input("Updated timezone regression"),admin);
        assertFalse(updated.updatedAt().isBefore(before.minusSeconds(1)));
        assertEquals(created.evaluatedAt(),updated.evaluatedAt());
    }
    @Test void onlyEvaluationKnownAtReportTimeIsUsedAndReadNeverChangesNotes() {
        String original=jdbc.queryForObject("SELECT evaluation_notes FROM plan_evaluations WHERE id=?",String.class,first);
        var old=reports.detail(first,admin);var recent=reports.detail(second,admin);
        assertEquals(new BigDecimal("50.00"),old.goals().getFirst().achievementPercent());
        assertEquals(new BigDecimal("90.00"),recent.goals().getFirst().achievementPercent());
        assertFalse(old.sections().structured());assertEquals(original,old.sections().originalText());
        assertEquals(original,jdbc.queryForObject("SELECT evaluation_notes FROM plan_evaluations WHERE id=?",String.class,first));
    }
    @Test void futureModificationIsMarkedUnavailableRatherThanLeakingLaterResults() {
        jdbc.update("UPDATE goal_evaluations SET achievement_percent=100,evaluation_notes='Future result',evaluated_at='2026-11-01',updated_at='2026-11-01' WHERE plan_goal_id=? AND attempt_no=1",goal);
        var old=reports.detail(first,admin);
        assertEquals("HISTORICAL_UNAVAILABLE",old.goals().getFirst().status());assertNull(old.goals().getFirst().achievementPercent());assertNull(old.goals().getFirst().notes());
        assertFalse(old.warnings().isEmpty());
    }
    @Test void goalsAddedAndReplacedAfterReportAreNotAppliedRetroactively() {
        var targets=jdbc.queryForList("SELECT id FROM goals WHERE is_active=1 AND id<>(SELECT goal_id FROM plan_goals WHERE id=?) ORDER BY id LIMIT 2",String.class,goal);
        assertEquals(2,targets.size());
        String old=assign(targets.get(0),LocalDateTime.of(2026,10,1,0,0),null);
        String replacement=assign(targets.get(1),LocalDateTime.of(2026,10,20,0,0),old);
        assertTrue(reports.detail(first,admin).goals().stream().anyMatch(g->g.id().equals(old)));
        assertTrue(reports.detail(first,admin).goals().stream().noneMatch(g->g.id().equals(replacement)));
        assertTrue(reports.detail(second,admin).goals().stream().noneMatch(g->g.id().equals(old)));
        assertTrue(reports.detail(second,admin).goals().stream().anyMatch(g->g.id().equals(replacement) && g.status().equals("NOT_STARTED")));
    }
    @Test void resultCutoffHonorsPlanEndDateInVietnamTimeRatherThanLaterEvaluationMonth() {
        jdbc.update("UPDATE learning_plans SET end_date='2026-10-12' WHERE id=?",plan);
        var detail=reports.detail(second,admin);
        assertEquals(LocalDateTime.of(2026,10,12,16,59,59,999999000),detail.resultsAsOf());
        assertEquals(new BigDecimal("50.00"),detail.goals().getFirst().achievementPercent());
    }
    @Test void assignedTeacherCanEditOwnReport() {
        assignTeacher(teacher);
        String own=id("plan_evaluations");
        jdbc.update("INSERT INTO plan_evaluations(id,learning_plan_id,evaluation_notes,evaluated_by,evaluated_at) VALUES(?,?,'Nhận xét của giáo viên',?,'2026-10-26')",own,plan,teacher.id());
        var detail=reports.detail(own,teacher);assertTrue(detail.editable());
        var saved=reports.update(own,new Edit("Tiến bộ được ghi nhận","Cần hỗ trợ","Đề xuất",detail.report().updatedAt()),teacher);
        assertEquals("Tiến bộ được ghi nhận",saved.sections().strengths());
    }
    @Test void editsUseExistingBusinessRulesPreserveOtherReportsAndRejectStaleVersions() {
        var before=reports.detail(second,admin);var goalData=jdbc.queryForList("SELECT * FROM goal_evaluations WHERE plan_goal_id=? ORDER BY attempt_no",goal);
        var input=new Edit("Tiến bộ thực tế\nDòng tiếp theo","Khó khăn","Đề xuất",before.report().updatedAt());
        var after=reports.update(second,input,admin);assertTrue(after.sections().structured());assertEquals(input.strengths(),after.sections().strengths());
        assertEquals("Nhận xét cũ cần giữ nguyên.",reports.detail(first,admin).sections().originalText());
        assertEquals(goalData,jdbc.queryForList("SELECT * FROM goal_evaluations WHERE plan_goal_id=? ORDER BY attempt_no",goal));
        assertEquals("ACTIVE",jdbc.queryForObject("SELECT status FROM learning_plans WHERE id=?",String.class,plan));
        assertEquals(409,assertThrows(ResponseStatusException.class,()->reports.update(second,input,admin)).getStatusCode().value());
        assertEquals(400,assertThrows(ResponseStatusException.class,()->reports.update(second,new Edit("","","",after.report().updatedAt()),admin)).getStatusCode().value());
    }
    @Test void assignedTeacherCanReadButOriginalAuthorAndPublicationProtectionStillApply() {
        assertEquals(403,assertThrows(ResponseStatusException.class,()->reports.detail(first,teacher)).getStatusCode().value());
        assignTeacher(teacher);assertEquals(2,reports.reports(student,teacher).size());
        assertFalse(reports.detail(second,teacher).editable());
        assertTrue(reports.students("",other).stream().noneMatch(s->s.id().equals(student)));
        assertEquals(403,assertThrows(ResponseStatusException.class,()->reports.update(second,new Edit("A","B","C",reports.detail(second,admin).report().updatedAt()),teacher)).getStatusCode().value());
        jdbc.update("UPDATE plan_evaluations SET sent_to_parent=1,sent_at=UTC_TIMESTAMP(6) WHERE id=?",first);
        var locked=reports.detail(first,admin);assertFalse(locked.editable());
        assertEquals(409,assertThrows(ResponseStatusException.class,()->reports.update(first,new Edit("A","B","C",locked.report().updatedAt()),admin)).getStatusCode().value());
    }
    @Test void endpointAuthorizationPdfDownloadAndMissingData() throws Exception {
        String url="/api/individual-reports/"+first;
        mvc.perform(get(url)).andExpect(status().isUnauthorized());
        mvc.perform(get(url).with(user(account("PARENT")))).andExpect(status().isForbidden());
        mvc.perform(get(url).with(user(other))).andExpect(status().isForbidden());
        mvc.perform(get(url+"/pdf").accept(MediaType.APPLICATION_PDF).with(user(other)))
            .andExpect(status().isForbidden()).andExpect(content().contentType(MediaType.APPLICATION_JSON));
        mvc.perform(get("/api/individual-reports/"+id("plan_evaluations")).with(user(admin))).andExpect(status().isNotFound());
        mvc.perform(get("/api/individual-reports/"+id("plan_evaluations")+"/pdf").accept(MediaType.APPLICATION_PDF).with(user(admin)))
            .andExpect(status().isNotFound()).andExpect(content().contentType(MediaType.APPLICATION_JSON));
        String original=reports.detail(first,admin).report().evaluationNotes();
        var response=mvc.perform(get(url+"/pdf?download=true").with(user(admin))).andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_PDF)).andExpect(header().string("Cache-Control","no-store"))
            .andExpect(header().string("Content-Disposition",org.hamcrest.Matchers.startsWith("attachment"))).andReturn().getResponse();
        assertTrue(response.getContentAsByteArray().length>1000);
        mvc.perform(get(url+"/pdf").with(user(admin))).andExpect(status().isOk()).andExpect(header().string("Content-Disposition",org.hamcrest.Matchers.startsWith("inline")));
        assertEquals(original,reports.detail(first,admin).report().evaluationNotes());
        var input=new Edit("A","B","C",reports.detail(second,admin).report().updatedAt());
        mvc.perform(put("/api/individual-reports/"+second+"/sections").with(user(admin)).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(input)))
            .andExpect(status().isForbidden());
        mvc.perform(put("/api/individual-reports/"+second+"/sections").with(user(admin)).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(input)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.sections.strengths").value("A"));
    }
}

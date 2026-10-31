package com.example.TTGDHNDuongAn.catalog;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.example.TTGDHNDuongAn.dto.GoalSelectionDtos.Target;
import com.example.TTGDHNDuongAn.dto.InterventionDtos.*;
import com.example.TTGDHNDuongAn.repository.PlanGoalRepository;
import com.example.TTGDHNDuongAn.security.AppUserPrincipal;
import com.example.TTGDHNDuongAn.service.InterventionService;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(properties={"spring.datasource.url=${GOAL_CATALOG_TEST_DB_URL}","SETUP_ADMIN_EMAIL=","SETUP_ADMIN_PASSWORD="})
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named="GOAL_CATALOG_TEST_DB_URL",matches=".+")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class InterventionWorkflowTests {
    static final String STUDENT="00000000-0000-0000-0000-000000000901";
    static final String OTHER="00000000-0000-0000-0000-000000000902";
    static final String ACTOR="00000000-0000-0000-0000-000000000903";
    static final String PATH="/api/intervention/students/"+STUDENT;
    static final AppUserPrincipal ADMIN=new AppUserPrincipal(ACTOR,"flow@example.invalid","Flow","ADMIN","unused");
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired InterventionService service;
    @MockitoSpyBean PlanGoalRepository assignments;
    final ObjectMapper mapper=new ObjectMapper();

    @BeforeAll void fixtures() {
        jdbc.update("INSERT INTO app_user(id,email,password_hash,display_name,role) VALUES(?,'flow@example.invalid','unused','Flow','ADMIN')",ACTOR);
        jdbc.update("INSERT INTO student(id,code,full_name,birth_date,admitted_on) VALUES(?,'FLOW1','Flow student','2022-01-01','2023-01-01'),(?,'FLOW2','Other student','2022-02-02','2023-01-01')",STUDENT,OTHER);
        jdbc.update("INSERT INTO programs(id,code,name) VALUES('PR0901','ESDM','Flow Denver'),('PR0902','VB','Flow VB')");
        jdbc.update("INSERT INTO program_levels(id,program_id,level_number,name,age_min_months,age_max_months,display_order) VALUES('LV0901','PR0901',1,'Age 1',12,24,1),('LV0902','PR0901',2,'Age 2',24,36,2),('LV0903','PR0902',1,'VB Age',0,48,1)");
        jdbc.update("INSERT INTO domains(id,program_id,code,name,display_order) VALUES('DM0901','PR0901','LANG','Language',1),('DM0903','PR0901','COG','Cognition',2),('DM0904','PR0902','VB','VB Language',1)");
        jdbc.update("INSERT INTO program_level_domains(id,program_id,program_level_id,domain_id) VALUES('LD0901','PR0901','LV0901','DM0901'),('LD0902','PR0901','LV0902','DM0901'),('LD0903','PR0901','LV0901','DM0903'),('LD0904','PR0902','LV0903','DM0904')");
        jdbc.update("INSERT INTO goals(id,level_domain_id,code,name,description,display_order) VALUES('GL000901','LD0901','FLOW.D1','First main','First description',1),('GL000902','LD0902','FLOW.D2','Second main',NULL,1),('GL000903','LD0903','FLOW.C1','Cognition main',NULL,1),('GL000904','LD0904','FLOW.V1','VB parent',NULL,1),('GL000905','LD0904','FLOW.V2','VB without children',NULL,2)");
        jdbc.update("INSERT INTO specific_goals(id,goal_id,code,description,display_order) VALUES('SG000901','GL000904','FLOW.V1A','Child A',1),('SG000902','GL000904','FLOW.V1B','Child B',2)");
    }
    @BeforeEach void clearPlans() {
        jdbc.update("DELETE FROM goal_evaluations WHERE plan_goal_id IN(SELECT pg.id FROM plan_goals pg JOIN learning_plans lp ON lp.id=pg.plan_id WHERE lp.student_id IN (?,?))",STUDENT,OTHER);
        jdbc.update("DELETE FROM plan_goals WHERE plan_id IN(SELECT id FROM learning_plans WHERE student_id IN (?,?))",STUDENT,OTHER);
        jdbc.update("DELETE FROM learning_plans WHERE student_id IN (?,?)",STUDENT,OTHER);
    }
    @AfterAll void cleanup() {
        clearPlans();
        jdbc.update("DELETE FROM specific_goals WHERE goal_id='GL000904'");
        jdbc.update("DELETE FROM goals WHERE level_domain_id IN ('LD0901','LD0902','LD0903','LD0904')");
        jdbc.update("DELETE FROM program_level_domains WHERE id IN ('LD0901','LD0902','LD0903','LD0904')");
        jdbc.update("DELETE FROM domains WHERE id IN ('DM0901','DM0902','DM0903','DM0904')");
        jdbc.update("DELETE FROM program_levels WHERE id IN ('LV0901','LV0902','LV0903')");
        jdbc.update("DELETE FROM programs WHERE id IN ('PR0901','PR0902')");
        jdbc.update("DELETE FROM student WHERE id IN (?,?)",STUDENT,OTHER);
        jdbc.update("DELETE FROM app_user WHERE id=?",ACTOR);
    }
    Draft draft() { return new Draft(UUID.randomUUID().toString(),"PR0901","Flow plan",LocalDate.of(2026,10,27),List.of(new Target("GL000901",null),new Target("GL000902",null))); }
    Complete complete(Review review) { return new Complete(review.requestId(),review.programId(),review.name(),review.startDate(),review.items().stream().map(row->new Target(row.goalId(),row.specificGoalId())).toList(),review.reviewToken()); }
    int plans() { return jdbc.queryForObject("SELECT COUNT(*) FROM learning_plans WHERE student_id=?",Integer.class,STUDENT); }
    int goals() { return jdbc.queryForObject("SELECT COUNT(*) FROM plan_goals pg JOIN learning_plans lp ON lp.id=pg.plan_id WHERE lp.student_id=?",Integer.class,STUDENT); }

    @Test void groupsDomainsAcrossDifferentCodesAndProvidesProgramStatisticsAndChoices() throws Exception {
        var summary=service.programs().stream().filter(p->p.id().equals("PR0901")).findFirst().orElseThrow();
        assertEquals(2,summary.domainCount()); assertEquals(3,summary.goalCount()); assertEquals(12,summary.ageMinMonths()); assertEquals(36,summary.ageMaxMonths());
        var fields=service.domains("PR0901"); assertEquals(2,fields.size()); assertEquals(2,fields.getFirst().ages().size());
        var denver=service.targets("LD0901"); assertFalse(denver.supportsSpecificGoals()); assertTrue(denver.goals().getFirst().canSelectGeneralGoal());
        var vb=service.targets("LD0904"); assertTrue(vb.supportsSpecificGoals()); assertTrue(vb.goals().getFirst().canSelectGeneralGoal()); assertTrue(vb.goals().get(1).canSelectGeneralGoal());
        mvc.perform(get("/api/intervention/students").param("q","FLOW1").with(user(ADMIN))).andExpect(status().isOk()).andExpect(jsonPath("$[0].studyingGoals").value(0));
    }
    @Test void reviewsWithoutWritingThenSavesThePlanAndAllGoalsAndRetries() throws Exception {
        var review=service.preview(STUDENT,draft()); assertEquals(0,plans()); assertEquals(0,goals());
        var saved=service.complete(STUDENT,complete(review),ACTOR); assertFalse(saved.alreadySaved()); assertEquals("ACTIVE",saved.result().plan().status());
        assertEquals(1,plans()); assertEquals(2,goals()); assertEquals(2,saved.result().goals().size());
        assertTrue(service.complete(STUDENT,complete(review),ACTOR).alreadySaved()); assertEquals(1,plans());
        assertEquals(2,service.workspace(STUDENT).student().studyingGoals()); assertEquals(0,service.workspace(OTHER).student().studyingGoals());
        jdbc.update("UPDATE goals SET description='Changed' WHERE id='GL000901'");
        try {
            var retry=service.complete(STUDENT,complete(review),ACTOR); assertTrue(retry.alreadySaved());
            assertTrue(retry.result().goals().stream().anyMatch(g->"First description".equals(g.goalSnapshot().path("description").asText())));
        } finally { jdbc.update("UPDATE goals SET description='First description' WHERE id='GL000901'"); }
        mvc.perform(get(PATH).with(user(ADMIN))).andExpect(status().isOk()).andExpect(jsonPath("$.plans[0].goals.length()").value(2));
    }
    @Test void bindsReviewToMetadataStudentAndLibraryAndRejectsInvalidVbSelections() {
        var draft=draft(); var review=service.preview(STUDENT,draft); var checked=complete(review);
        assertThrows(ResponseStatusException.class,()->service.complete(STUDENT,new Complete(checked.requestId(),checked.programId(),"Changed",checked.startDate(),checked.selections(),checked.reviewToken()),ACTOR));
        assertThrows(ResponseStatusException.class,()->service.complete(OTHER,checked,ACTOR)); assertEquals(0,plans());
        jdbc.update("UPDATE goals SET description='New text' WHERE id='GL000901'");
        try { assertThrows(ResponseStatusException.class,()->service.complete(STUDENT,checked,ACTOR)); }
        finally { jdbc.update("UPDATE goals SET description='First description' WHERE id='GL000901'"); }
        assertEquals(0,plans()); assertEquals(0,goals());
        var vb=new Draft(UUID.randomUUID().toString(),"PR0902","VB plan",draft.startDate(),List.of(new Target("GL000904",null)));
        assertEquals(1,service.preview(STUDENT,vb).total());
        var valid=new Draft(vb.requestId(),vb.programId(),vb.name(),vb.startDate(),List.of(new Target("GL000904","SG000901"),new Target("GL000904","SG000902"),new Target("GL000905",null)));
        assertEquals(3,service.complete(STUDENT,complete(service.preview(STUDENT,valid)),ACTOR).result().goals().size());
    }
    @Test void rollsBackBothTablesWhenASecondGoalCannotBeInserted() {
        var checked=complete(service.preview(STUDENT,draft()));
        doThrow(new DataIntegrityViolationException("Simulated second goal failure")).when(assignments).insert(anyString(),eq("GL000902"),isNull(),eq(ACTOR));
        try { assertThrows(DataIntegrityViolationException.class,()->service.complete(STUDENT,checked,ACTOR)); }
        finally { reset(assignments); }
        assertEquals(0,plans()); assertEquals(0,goals());
    }
    @Test void concurrentFinishCreatesOnePlanAndStatusCountsExcludePausedAndCancelled() throws Exception {
        var checked=complete(service.preview(STUDENT,draft()));
        try(var pool=Executors.newFixedThreadPool(2)) {
            var a=pool.submit(()->service.complete(STUDENT,checked,ACTOR)); var b=pool.submit(()->service.complete(STUDENT,checked,ACTOR));
            assertNotEquals(a.get().alreadySaved(),b.get().alreadySaved());
        }
        assertEquals(1,plans()); assertEquals(2,goals());
        jdbc.update("INSERT INTO goal_evaluations(id,plan_goal_id,attempt_no,achievement_percent,evaluated_at,evaluated_by) SELECT ?,id,1,80,UTC_TIMESTAMP(6),? FROM plan_goals WHERE goal_id='GL000901' AND plan_id=?",UUID.randomUUID().toString(),ACTOR,checked.requestId());
        assertEquals(1,service.workspace(STUDENT).student().studyingGoals()); assertEquals(1,service.workspace(STUDENT).student().achievedGoals());
        assertEquals(1,service.workspace(STUDENT).student().studyingGoals());
        jdbc.update("UPDATE learning_plans SET status='CANCELLED' WHERE id=?",checked.requestId());
        assertEquals(0,service.workspace(STUDENT).student().achievedGoals());
    }
    @Test void enforcesRolesCsrfAndValidatesTheFinalRequest() throws Exception {
        mvc.perform(get("/api/intervention/students")).andExpect(status().isUnauthorized());
        for(String role:List.of("PARENT")) mvc.perform(get(PATH).with(user("forbidden").roles(role))).andExpect(status().isForbidden());
        mvc.perform(post(PATH+"/complete").with(user(ADMIN)).contentType(MediaType.APPLICATION_JSON).content("{}")) .andExpect(status().isForbidden());
        mvc.perform(post(PATH+"/preview").with(user(ADMIN)).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{}")) .andExpect(status().isBadRequest());
        var review=service.preview(STUDENT,draft());
        mvc.perform(post(PATH+"/complete").with(user(ADMIN)).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(complete(review))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.result.goals.length()").value(2));
    }

    @Test void validatesSharedDomainSchemaAndRejectsCrossProgramLinksAtDatabaseAndApi() throws Exception {
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND ((table_name='domains' AND column_name='program_level_id') OR (table_name='goals' AND column_name='domain_id'))",Integer.class));
        assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM program_level_domains WHERE domain_id='DM0901'",Integer.class));
        assertEquals("GL000902",service.targets("LD0902").goals().getFirst().id());
        assertThrows(org.springframework.dao.DataAccessException.class,()->jdbc.update("INSERT INTO program_level_domains(id,program_id,program_level_id,domain_id) VALUES('LD0999','PR0901','LV0901','DM0904')"));
        mvc.perform(post("/api/levels/LV0901/domain-links").with(user(ADMIN)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content("{\"domainId\":\"DM0904\",\"displayOrder\":1}"))
            .andExpect(status().isConflict());
        mvc.perform(get("/api/programs/PR0901/domains").with(user(ADMIN)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2));
    }

    @Test void editsPlanTracksObservationsAndRetainsImmutableHierarchy() throws Exception {
        var saved=service.complete(STUDENT,complete(service.preview(STUDENT,draft())),ACTOR);
        String id=saved.result().plan().id(),goal=saved.result().goals().getFirst().id();
        var original=saved.result().goals().getFirst().goalSnapshot();
        assertTrue(original.has("level_domain_id")); assertEquals(5,original.path("schema_version").asInt());
        mvc.perform(patch("/api/plans/"+id).with(user(ADMIN)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content("{\"name\":\"Edited plan\",\"startDate\":\"2026-10-27\",\"status\":\"ACTIVE\"}"))
            .andExpect(status().isNoContent());
        mvc.perform(post("/api/plans/"+id+"/goals/"+goal+"/evaluations").with(user(ADMIN)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content("{\"evaluationNotes\":\"Real observation\",\"achievementPercent\":79.99}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$[0].resultStatus").value("NOT_ACHIEVED"));
        mvc.perform(get("/api/plans/"+id+"/goals/"+goal+"/evaluations").with(user(ADMIN)))
            .andExpect(status().isOk()).andExpect(jsonPath("$[0].evaluationNotes").value("Real observation"));
        mvc.perform(post("/api/plans/"+id+"/goals/"+goal+"/evaluations").with(user(ADMIN)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content("{\"achievementPercent\":101}")) .andExpect(status().isBadRequest());
        mvc.perform(get("/api/plans/"+id+"/goals/"+goal+"/evaluations").with(user("parent").roles("PARENT")))
            .andExpect(status().isForbidden());
        var reread=service.workspace(STUDENT).plans().getFirst();
        assertEquals("Edited plan",reread.plan().name());
        assertEquals(original,reread.goals().stream().filter(g->g.id().equals(goal)).findFirst().orElseThrow().goalSnapshot());
    }
}

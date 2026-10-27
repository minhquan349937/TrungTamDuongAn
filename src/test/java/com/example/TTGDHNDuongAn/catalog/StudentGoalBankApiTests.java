package com.example.TTGDHNDuongAn.catalog;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import com.example.TTGDHNDuongAn.dto.GoalBankDtos.*;
import com.example.TTGDHNDuongAn.security.AppUserPrincipal;
import com.example.TTGDHNDuongAn.service.StudentGoalBankService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(properties="spring.datasource.url=${GOAL_CATALOG_TEST_DB_URL}")
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named="GOAL_CATALOG_TEST_DB_URL",matches=".+")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class StudentGoalBankApiTests {
    static final String STUDENT="00000000-0000-0000-0000-000000000801";
    static final String OTHER="00000000-0000-0000-0000-000000000802";
    static final String ACTOR="00000000-0000-0000-0000-000000000803";
    static final String PLAN="00000000-0000-0000-0000-000000000804";
    static final String PATH="/api/students/"+STUDENT+"/goal-bank";
    static final AppUserPrincipal ADMIN=new AppUserPrincipal(ACTOR,"bank-test@example.invalid","Test","ADMIN","unused");
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired StudentGoalBankService service;
    final ObjectMapper mapper=new ObjectMapper();

    @BeforeAll void fixtures() {
        jdbc.update("INSERT INTO app_user(id,email,password_hash,display_name,role) VALUES(?,'bank-test@example.invalid','unused','Test','ADMIN')",ACTOR);
        jdbc.update("INSERT INTO student(id,code,full_name,birth_date,admitted_on) VALUES(?,'BANK-TEST','Test','2022-01-01','2023-01-01'),(?,'BANK-OTHER','Other','2022-01-01','2023-01-01')",STUDENT,OTHER);
        jdbc.update("INSERT INTO learning_plans(id,student_id,name,start_date,created_by) VALUES(?,?,'Test','2026-01-01',?)",PLAN,STUDENT,ACTOR);
        jdbc.update("INSERT INTO programs(id,code,name) VALUES('PR0801','ESDM','Denver test'),('PR0802','VB','VB test')");
        jdbc.update("INSERT INTO program_levels(id,program_id,level_number,name,age_min_months,age_max_months,display_order) VALUES('LV0801','PR0801',1,'Test',12,24,1),('LV0802','PR0802',1,'Test',0,48,1)");
        jdbc.update("INSERT INTO domains(id,program_id,code,name) VALUES('DM0801','PR0801','TEST','Test'),('DM0802','PR0802','TEST','Test')");
        jdbc.update("INSERT INTO program_level_domains(id,program_id,program_level_id,domain_id) VALUES('LD0801','PR0801','LV0801','DM0801'),('LD0802','PR0802','LV0802','DM0802')");
        jdbc.update("INSERT INTO goals(id,level_domain_id,code,name,description,display_order) VALUES('GL000801','LD0801','TEST.D1','Original main name','Original description',1),('GL000802','LD0801','TEST.D2','Second main goal',NULL,2),('GL000803','LD0802','TEST.VB1','VB parent',NULL,1),('GL000804','LD0802','TEST.VB2','VB no children',NULL,2)");
        jdbc.update("INSERT INTO specific_goals(id,goal_id,code,description,display_order) VALUES('SG000801','GL000803','TEST.A','Child A',1),('SG000802','GL000803','TEST.B','Child B',2),('SG000803','GL000803','TEST.C','Child C',3),('SG000804','GL000803','TEST.D','Child D',4)");
    }
    @BeforeEach void clearBank() {
        jdbc.update("DELETE FROM student_goal_bank WHERE student_id IN (?,?)",STUDENT,OTHER);
        jdbc.update("DELETE FROM plan_goals WHERE plan_id=?",PLAN);
    }
    @AfterAll void cleanup() {
        clearBank();
        jdbc.update("DELETE FROM learning_plans WHERE id=?",PLAN);
        jdbc.update("DELETE FROM specific_goals WHERE goal_id='GL000803'");
        jdbc.update("DELETE FROM goals WHERE level_domain_id IN ('LD0801','LD0802')");
        jdbc.update("DELETE FROM program_level_domains WHERE id IN ('LD0801','LD0802')");
        jdbc.update("DELETE FROM domains WHERE id IN ('DM0801','DM0802')");
        jdbc.update("DELETE FROM program_levels WHERE id IN ('LV0801','LV0802')");
        jdbc.update("DELETE FROM programs WHERE id IN ('PR0801','PR0802')");
        jdbc.update("DELETE FROM student WHERE id IN (?,?)",STUDENT,OTHER);
        jdbc.update("DELETE FROM app_user WHERE id=?",ACTOR);
    }
    Review preview(String program,List<Target> targets) throws Exception {
        var response=mvc.perform(post(PATH+"/preview").with(user(ADMIN)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(new ReviewRequest(program,targets))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return mapper.readValue(response,Review.class);
    }
    CompleteRequest completeRequest(Review review) {
        return new CompleteRequest(review.programId(),review.items().stream().map(row -> new Target(row.goalId(),row.specificGoalId())).toList(),review.reviewToken());
    }
    int bankCount() { return jdbc.queryForObject("SELECT COUNT(*) FROM student_goal_bank WHERE student_id=?",Integer.class,STUDENT); }

    @Test void previewsWithoutWritingThenCompletesAndRetriesWithoutDuplicates() throws Exception {
        var review=preview("PR0801",List.of(new Target("GL000801",null),new Target("GL000801",null)));
        assertEquals(0,bankCount()); assertEquals(1,review.total());
        assertEquals("Original description",review.items().getFirst().description());
        String body=mapper.writeValueAsString(completeRequest(review));
        mvc.perform(post(PATH+"/complete").with(user(ADMIN)).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.addedCount").value(1));
        mvc.perform(post(PATH+"/complete").with(user(ADMIN)).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.addedCount").value(0)).andExpect(jsonPath("$.alreadyPresentCount").value(1));
        mvc.perform(get(PATH).with(user(ADMIN))).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
        mvc.perform(get("/api/students/"+OTHER+"/goal-bank").with(user(ADMIN))).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
    }

    @Test void vbSupportsFourChildrenOrMainGoalWithoutChildrenAndChecksParentAndProgram() throws Exception {
        mvc.perform(get("/api/goals/GL000803").with(user(ADMIN))).andExpect(status().isOk())
                .andExpect(jsonPath("$.supportsSpecificGoals").value(true)).andExpect(jsonPath("$.canSelectGeneralGoal").value(false))
                .andExpect(jsonPath("$.specificGoals.length()").value(4)).andExpect(jsonPath("$.specificGoals[0].code").value("TEST.A"));
        var review=preview("PR0802",List.of(new Target("GL000803","SG000801"),new Target("GL000804",null)));
        assertEquals(0,review.items().getFirst().ageMinMonths());
        var result=service.complete(STUDENT,completeRequest(review),ACTOR);
        assertEquals(2,result.addedCount());
        mvc.perform(post(PATH+"/preview").with(user(ADMIN)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(new ReviewRequest("PR0802",List.of(new Target("GL000803",null))))))
                .andExpect(status().isBadRequest());
        mvc.perform(post(PATH+"/preview").with(user(ADMIN)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(new ReviewRequest("PR0802",List.of(new Target("GL000804","SG000801"))))))
                .andExpect(status().isConflict());
        mvc.perform(post(PATH+"/preview").with(user(ADMIN)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(new ReviewRequest("PR0801",List.of(new Target("GL000803","SG000801"))))))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/plans/"+PLAN+"/goals").with(user(ADMIN)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"goalId\":\"GL000803\",\"specificGoalId\":\"SG000801\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.goalId").value("GL000803"));
    }

    @Test void rejectsStaleReviewAndKeepsSavedSnapshotsWhenLibraryChanges() throws Exception {
        var review=preview("PR0801",List.of(new Target("GL000801",null)));
        jdbc.update("UPDATE goals SET name='Changed after review' WHERE id='GL000801'");
        try {
            mvc.perform(post(PATH+"/complete").with(user(ADMIN)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                            .content(mapper.writeValueAsString(completeRequest(review)))).andExpect(status().isConflict());
            assertEquals(0,bankCount());
        } finally { jdbc.update("UPDATE goals SET name='Original main name' WHERE id='GL000801'"); }
        service.complete(STUDENT,completeRequest(review),ACTOR);
        jdbc.update("UPDATE goals SET description='Changed after save' WHERE id='GL000801'");
        try {
            mvc.perform(get(PATH).with(user(ADMIN))).andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].goalSnapshot.description").value("Original description"));
        } finally { jdbc.update("UPDATE goals SET description='Original description' WHERE id='GL000801'"); }
    }

    @Test void rejectsTamperedSelectionsAndWrongStudentAndLeavesTheBankUnchanged() throws Exception {
        var review=preview("PR0801",List.of(new Target("GL000801",null)));
        var changed=new CompleteRequest("PR0801",List.of(new Target("GL000801",null),new Target("GL000802",null)),review.reviewToken());
        mvc.perform(post(PATH+"/complete").with(user(ADMIN)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(changed))).andExpect(status().isConflict());
        mvc.perform(post("/api/students/"+OTHER+"/goal-bank/complete").with(user(ADMIN)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(completeRequest(review)))).andExpect(status().isConflict());
        assertEquals(0,bankCount());
    }

    @Test void concurrentFinishesAreIdempotent() throws Exception {
        var review=preview("PR0801",List.of(new Target("GL000801",null),new Target("GL000802",null)));
        var request=completeRequest(review);
        List<Callable<Completion>> actions=List.of(() -> service.complete(STUDENT,request,ACTOR),() -> service.complete(STUDENT,request,ACTOR));
        try(var pool=Executors.newFixedThreadPool(2)) {
            var futures=pool.invokeAll(actions);
            assertEquals(2,futures.get(0).get().addedCount()+futures.get(1).get().addedCount());
        }
        assertEquals(2,bankCount());
    }

    @Test void validatesInputAndEnforcesExistingRolesAndCsrf() throws Exception {
        for(String role:List.of("TEACHER","PARENT")) {
            mvc.perform(get(PATH).with(user("reader").roles(role))).andExpect(status().isForbidden());
            mvc.perform(post(PATH+"/preview").with(user("reader").roles(role)).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isForbidden());
        }
        mvc.perform(post(PATH+"/complete").with(user(ADMIN)).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
        mvc.perform(post(PATH+"/preview").with(user(ADMIN)).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"programId\":\"PR0801\",\"selections\":[]}"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/students/bad/goal-bank").with(user(ADMIN))).andExpect(status().isBadRequest());
        mvc.perform(get("/api/students/00000000-0000-0000-0000-000000000899/goal-bank").with(user(ADMIN))).andExpect(status().isNotFound());
        assertEquals(0,bankCount());
    }
}

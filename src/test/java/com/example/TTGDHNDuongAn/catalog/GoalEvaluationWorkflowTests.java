package com.example.TTGDHNDuongAn.catalog;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.example.TTGDHNDuongAn.dto.GoalSelectionDtos.Target;
import com.example.TTGDHNDuongAn.dto.InterventionDtos.*;
import com.example.TTGDHNDuongAn.dto.GoalEvaluationDtos.Input;
import com.example.TTGDHNDuongAn.security.AppUserPrincipal;
import com.example.TTGDHNDuongAn.service.InterventionService;
import com.example.TTGDHNDuongAn.service.GoalEvaluationService;
import com.example.TTGDHNDuongAn.service.IndividualAssignmentService;
import com.example.TTGDHNDuongAn.service.StudentGoalHistoryService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataAccessException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Uses an isolated clone of the imported catalog. Every test rolls back its fixtures. */
@SpringBootTest(properties={"spring.datasource.url=${GOAL_EVALUATION_TEST_DB_URL}","SETUP_ADMIN_EMAIL=","SETUP_ADMIN_PASSWORD=",
    "spring.docker.compose.enabled=false","logging.level.root=WARN","debug=false"})
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named="GOAL_EVALUATION_TEST_DB_URL",matches=".+")
@Transactional
class GoalEvaluationWorkflowTests {
    @Autowired JdbcTemplate jdbc;
    @Autowired InterventionService workflow;
    @Autowired GoalEvaluationService evaluations;
    @Autowired IndividualAssignmentService assignments;
    @Autowired StudentGoalHistoryService history;
    @Autowired MockMvc mvc;
    String student,otherStudent,denver,vb,child,plan,goal,teacherStaff,otherTeacherStaff;
    AppUserPrincipal admin,teacher,otherTeacher;
    String id() { return UUID.randomUUID().toString(); }
    AppUserPrincipal account(String role) {
        String id=id(),email=id+"@evaluation.invalid";
        jdbc.update("INSERT INTO app_user(id,email,password_hash,display_name,role) VALUES(?,?,'unused',?,?)",id,email,role,role);
        return new AppUserPrincipal(id,email,role,role,"unused");
    }
    @BeforeEach void fixture() {
        admin=account("ADMIN");teacher=account("TEACHER");otherTeacher=account("TEACHER");
        student=id();otherStudent=id();
        for(String studentId:List.of(student,otherStudent)) jdbc.update("INSERT INTO student(id,code,full_name,birth_date,admitted_on) VALUES(?,?,'Evaluation test','2022-01-01','2023-01-01')",studentId,"EV-"+studentId.substring(0,8));
        teacherStaff=id();otherTeacherStaff=id();
        jdbc.update("INSERT INTO staff(id,code,user_id,full_name,joined_on) VALUES(?,?,?,'Assigned teacher','2023-01-01'),(?,?,?,'Other teacher','2023-01-01')",
            teacherStaff,"EV-"+teacherStaff.substring(0,8),teacher.id(),otherTeacherStaff,"EV-"+otherTeacherStaff.substring(0,8),otherTeacher.id());
        assignments.create(new IndividualAssignmentService.Create(student,teacherStaff));
        denver=jdbc.queryForObject("SELECT g.id FROM goals g JOIN program_level_domains ld ON ld.id=g.level_domain_id JOIN programs p ON p.id=ld.program_id WHERE p.code IN ('ESDM','DENVER') AND g.is_active=1 ORDER BY g.id LIMIT 1",String.class);
        var target=jdbc.queryForMap("SELECT g.id,sg.id child FROM goals g JOIN specific_goals sg ON sg.goal_id=g.id JOIN program_level_domains ld ON ld.id=g.level_domain_id JOIN programs p ON p.id=ld.program_id WHERE p.code='VB' AND g.is_active=1 AND sg.is_active=1 ORDER BY sg.id LIMIT 1");
        vb=target.get("id").toString();child=target.get("child").toString();
        var saved=createPlan();plan=saved.result().plan().id();goal=saved.result().goals().stream().filter(g->g.goalId().equals(denver)).findFirst().orElseThrow().id();
    }
    Saved createPlan() {
        String program=jdbc.queryForObject("SELECT program_id FROM program_level_domains ld JOIN goals g ON g.level_domain_id=ld.id WHERE g.id=?",String.class,denver);
        var draft=new Draft(id(),program,"Combined Denver and VB",LocalDate.of(2026,10,28),
            List.of(new Target(denver,null),new Target(vb,null),new Target(vb,child)));
        var review=workflow.preview(student,draft);
        return workflow.complete(student,new Complete(review.requestId(),review.programId(),review.name(),review.startDate(),
            review.items().stream().map(t->new Target(t.goalId(),t.specificGoalId())).toList(),review.reviewToken()),admin.id());
    }
    Input input(String percent) { return new Input(percent==null?null:new BigDecimal(percent),"RETRY","Teacher notes"); }
    String endpoint() { return "/api/plans/"+plan+"/goals/"+goal+"/evaluations"; }

    @Test void createsManyGoalsAcrossProgramsAndPreservesHierarchyAndMultiplePlans() {
        var workspace=workflow.workspace(student);
        assertEquals(3,workspace.plans().getFirst().goals().size());
        for(var item:workspace.plans().getFirst().goals()) {
            assertTrue(item.goalSnapshot().has("program_name"));assertTrue(item.goalSnapshot().has("level_name"));
            assertTrue(item.goalSnapshot().has("domain_name"));
            if(item.goalId().equals(denver)) assertNull(item.specificGoalId());
            if(item.specificGoalId()!=null) assertEquals(vb,item.goalId());
        }
        createPlan();assertEquals(2,workflow.workspace(student).plans().size());
    }
    @Test void evaluatesBoundariesAndRetainsEveryAttemptAndLatestStatus() {
        String[] percentages={null,"0","79.99","80","100"};
        String[] statuses={"NOT_STARTED","NOT_ACHIEVED","NOT_ACHIEVED","ACHIEVED","ACHIEVED"};
        for(int i=0;i<percentages.length;i++) {
            var rows=evaluations.create(plan,goal,input(percentages[i]),teacher);
            assertEquals(i+1,rows.size());assertEquals(i+1,rows.getFirst().attemptNo());
            assertEquals(statuses[i],rows.getFirst().resultStatus());
            assertEquals(statuses[i],jdbc.queryForObject("SELECT status FROM plan_goals WHERE id=?",String.class,goal));
            var summary=workflow.workspace(student).plans().stream().flatMap(p->p.goals().stream())
                .filter(g->g.id().equals(goal)).findFirst().orElseThrow();
            if(percentages[i]==null) assertNull(summary.achievementPercent());
            else assertEquals(0,new BigDecimal(percentages[i]).compareTo(summary.achievementPercent()));
        }
        var rows=evaluations.list(plan,goal,teacher);
        evaluations.update(plan,goal,rows.getLast().id(),input("50"),teacher);
        assertEquals(0,new BigDecimal("100").compareTo(workflow.workspace(student).plans().stream()
            .flatMap(p->p.goals().stream()).filter(g->g.id().equals(goal)).findFirst().orElseThrow().achievementPercent()));
        assertEquals("ACHIEVED",jdbc.queryForObject("SELECT status FROM plan_goals WHERE id=?",String.class,goal));
        assertEquals(0,new BigDecimal("100").compareTo(history.list(student).stream().filter(h->h.key().equals("G:"+denver)).findFirst().orElseThrow().achievementPercent()));
        evaluations.update(plan,goal,rows.getFirst().id(),input("50"),teacher);
        assertEquals("NOT_ACHIEVED",jdbc.queryForObject("SELECT status FROM plan_goals WHERE id=?",String.class,goal));
        assertEquals(5,evaluations.list(plan,goal,teacher).size());
    }
    @Test void enforcesTeacherAssignmentOwnershipCrossPlanAndCsrf() throws Exception {
        mvc.perform(get(endpoint()).with(user(otherTeacher))).andExpect(status().isForbidden());
        mvc.perform(post(endpoint()).with(user(teacher)).contentType(MediaType.APPLICATION_JSON).content("{\"achievementPercent\":80}"))
            .andExpect(status().isForbidden());
        mvc.perform(post(endpoint()).with(user(teacher)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content("{\"achievementPercent\":50,\"resultStatus\":\"ACHIEVED\",\"restartDecision\":\"RETRY\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$[0].resultStatus").value("NOT_ACHIEVED"));
        mvc.perform(get(endpoint()).with(user(teacher))).andExpect(status().isOk()).andExpect(jsonPath("$[0].attemptNo").value(1));
        mvc.perform(get(endpoint()).with(user("parent").roles("PARENT"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/intervention/students/"+otherStudent).with(user(teacher))).andExpect(status().isForbidden());
        mvc.perform(get("/api/intervention/students").with(user(teacher))).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
        var otherPlan=createPlan().result().plan().id();
        var adminRow=evaluations.create(plan,goal,input("90"),admin).getFirst();
        mvc.perform(put(endpoint()+"/"+adminRow.id()).with(user(teacher)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content("{\"achievementPercent\":10}")) .andExpect(status().isForbidden());
        mvc.perform(get("/api/plans/"+otherPlan+"/goals/"+goal+"/evaluations").with(user(teacher))).andExpect(status().isNotFound());
    }
    @Test void rejectsInvalidInputsAndManualDatabaseStatusChanges() throws Exception {
        for(String value:List.of("-1","100.01","80.001")) {
            mvc.perform(post(endpoint()).with(user(teacher)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"achievementPercent\":"+value+"}")) .andExpect(status().isBadRequest());
        }
        assertThrows(ResponseStatusException.class,()->evaluations.create(plan,goal,new Input(BigDecimal.TEN,"OTHER",null),teacher));
        assertThrows(DataAccessException.class,()->jdbc.update("UPDATE plan_goals SET status='ACHIEVED' WHERE id=?",goal));
        assertThrows(DataAccessException.class,()->jdbc.update("INSERT INTO goal_evaluations(id,plan_goal_id,attempt_no,achievement_percent,evaluated_at,evaluated_by) " +
            "VALUES(?,?,1,101,UTC_TIMESTAMP(6),?)",id(),goal,teacher.id()));
        assertThrows(DataAccessException.class,()->jdbc.update("INSERT INTO goal_evaluations(id,plan_goal_id,attempt_no,result_status,evaluated_by) " +
            "VALUES(?,?,1,'ACHIEVED',?)",id(),goal,teacher.id()));
        assertEquals(0,evaluations.list(plan,goal,teacher).size());
    }

    @Test void adminAssignmentApiGrantsAndRevokesTeacherScope() throws Exception {
        String root="/api/admin/individual-assignments";
        mvc.perform(get(root).with(user(teacher))).andExpect(status().isForbidden());
        mvc.perform(get(root+"/teachers").with(user(admin))).andExpect(status().isOk()).andExpect(jsonPath("$").isArray());
        mvc.perform(post(root).with(user(admin)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content("{\"studentId\":\""+student+"\",\"teacherId\":\""+teacherStaff+"\"}")) .andExpect(status().isConflict());
        mvc.perform(post(root).with(user(admin)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content("{\"studentId\":\""+otherStudent+"\",\"teacherId\":\""+teacherStaff+"\"}")) .andExpect(status().isOk());
        mvc.perform(get("/api/intervention/students/"+otherStudent).with(user(teacher))).andExpect(status().isOk());
        var assignment=assignments.list().stream().filter(a->a.studentId().equals(student)).findFirst().orElseThrow();
        mvc.perform(put(root+"/"+assignment.id()).with(user(admin)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content("{\"teacherId\":\""+otherTeacherStaff+"\"}")) .andExpect(status().isOk());
        mvc.perform(get(endpoint()).with(user(teacher))).andExpect(status().isForbidden());
        mvc.perform(get(endpoint()).with(user(otherTeacher))).andExpect(status().isOk());
    }

    @Test void newPlanChoicesDistinguishUnperformedNewAndFailedTargetsAndHideAchieved() throws Exception {
        String domain=jdbc.queryForObject("SELECT level_domain_id FROM goals WHERE id=?",String.class,denver);
        var initial=history.targets(student,workflow.targets(domain));
        assertEquals("NOT_STARTED",initial.history().stream().filter(h->h.key().equals("G:"+denver)).findFirst().orElseThrow().resultStatus());
        String neverAssigned=jdbc.queryForObject("SELECT id FROM goals WHERE level_domain_id=? AND id<>? AND is_active=1 ORDER BY id LIMIT 1",String.class,domain,denver);
        assertTrue(initial.goals().stream().anyMatch(g->g.id().equals(neverAssigned)));
        assertFalse(initial.history().stream().anyMatch(h->h.goalId().equals(neverAssigned)));
        evaluations.create(plan,goal,input("55"),teacher);
        mvc.perform(get("/api/intervention/students/"+student+"/level-domains/"+domain+"/targets").with(user(teacher)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.history[0].resultStatus").value("NOT_ACHIEVED"))
            .andExpect(jsonPath("$.history[0].achievementPercent").value(55));
        mvc.perform(get("/api/intervention/students/"+student+"/level-domains/"+domain+"/targets").with(user(otherTeacher)))
            .andExpect(status().isForbidden());
        evaluations.create(plan,goal,input("80"),teacher);
        assertFalse(history.targets(student,workflow.targets(domain)).goals().stream().anyMatch(g->g.id().equals(denver)));
        assertTrue(workflow.targets(domain).goals().stream().anyMatch(g->g.id().equals(denver))); // shared library unchanged
        assertTrue(history.list(otherStudent).isEmpty());
    }

    @Test void vbMainAndChildHaveIndependentHistoryAndCancelledPlansDoNotContribute() {
        var goals=workflow.workspace(student).plans().getFirst().goals();
        var main=goals.stream().filter(g->g.goalId().equals(vb)&&g.specificGoalId()==null).findFirst().orElseThrow();
        var specific=goals.stream().filter(g->child.equals(g.specificGoalId())).findFirst().orElseThrow();
        evaluations.create(plan,main.id(),input("85"),teacher);
        evaluations.create(plan,specific.id(),input("40"),teacher);
        String domain=jdbc.queryForObject("SELECT level_domain_id FROM goals WHERE id=?",String.class,vb);
        var choices=history.targets(student,workflow.targets(domain));
        var parent=choices.goals().stream().filter(g->g.id().equals(vb)).findFirst().orElseThrow();
        assertFalse(parent.canSelectGeneralGoal());assertTrue(parent.hasSpecificGoals());
        assertEquals("NOT_ACHIEVED",choices.history().stream().filter(h->child.equals(h.specificGoalId())).findFirst().orElseThrow().resultStatus());
        jdbc.update("UPDATE learning_plans SET status='CANCELLED' WHERE id=?",plan);
        assertTrue(history.list(student).isEmpty());
    }

    @Test void newlyPlannedAndNullAttemptsKeepTheLastMeasuredResultAndStaleReviewsAreRejected() {
        evaluations.create(plan,goal,input("45"),teacher);
        var next=createPlan().result();
        var nextGoal=next.goals().stream().filter(g->g.goalId().equals(denver)).findFirst().orElseThrow();
        evaluations.create(next.plan().id(),nextGoal.id(),input(null),teacher);
        assertEquals(0,new BigDecimal("45").compareTo(history.list(student).stream().filter(h->h.key().equals("G:"+denver)).findFirst().orElseThrow().achievementPercent()));
        String program=jdbc.queryForObject("SELECT program_id FROM program_level_domains ld JOIN goals g ON g.level_domain_id=ld.id WHERE g.id=?",String.class,denver);
        var draft=new Draft(id(),program,"Next plan",LocalDate.now(),List.of(new Target(denver,null)));
        var review=workflow.preview(student,draft);
        evaluations.create(next.plan().id(),nextGoal.id(),input("90"),teacher);
        var request=new Complete(review.requestId(),review.programId(),review.name(),review.startDate(),draft.selections(),review.reviewToken());
        assertEquals(409,assertThrows(ResponseStatusException.class,()->workflow.complete(student,request,admin.id())).getStatusCode().value());
        assertEquals(409,assertThrows(ResponseStatusException.class,()->workflow.preview(student,draft)).getStatusCode().value());
        assertEquals(2,workflow.workspace(student).plans().size());
    }
}

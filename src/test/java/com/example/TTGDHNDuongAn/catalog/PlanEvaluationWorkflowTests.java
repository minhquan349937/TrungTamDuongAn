package com.example.TTGDHNDuongAn.catalog;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.example.TTGDHNDuongAn.dto.PlanEvaluationDtos.Input;
import com.example.TTGDHNDuongAn.dto.PlanGoalDtos.AssignGoal;
import com.example.TTGDHNDuongAn.dto.PlanProgressDtos.EditPlan;
import com.example.TTGDHNDuongAn.dto.GoalEvaluationDtos;
import com.example.TTGDHNDuongAn.security.AppUserPrincipal;
import com.example.TTGDHNDuongAn.service.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@SpringBootTest(properties={"spring.datasource.url=${PLAN_EVALUATION_TEST_DB_URL}","SETUP_ADMIN_EMAIL=","SETUP_ADMIN_PASSWORD=",
    "spring.docker.compose.enabled=false","logging.level.root=WARN","debug=false"})
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named="PLAN_EVALUATION_TEST_DB_URL",matches=".+")
@Transactional
class PlanEvaluationWorkflowTests {
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @Autowired PlanEvaluationService reports;
    @Autowired GoalEvaluationService goals;
    @Autowired PlanGoalService planGoals;
    @Autowired PlanProgressService planProgress;
    @Autowired IndividualAssignmentService assignments;
    @Autowired InterventionService workflow;
    AppUserPrincipal admin,teacher,otherTeacher,parent,otherParent;
    String plan,student,goal,guardian,staff;
    String id(){return UUID.randomUUID().toString();}
    AppUserPrincipal userAccount(String role) {
        String id=id(),email=id+"@test.invalid";
        jdbc.update("INSERT INTO app_user(id,email,password_hash,display_name,role) VALUES(?,?,'unused',?,?)",id,email,role,role);
        return new AppUserPrincipal(id,email,role,role,"unused");
    }
    @BeforeEach void setup() {
        admin=userAccount("ADMIN");teacher=userAccount("TEACHER");otherTeacher=userAccount("TEACHER");
        parent=userAccount("PARENT");otherParent=userAccount("PARENT");
        plan=jdbc.queryForObject("SELECT id FROM learning_plans ORDER BY id LIMIT 1",String.class);
        student=jdbc.queryForObject("SELECT student_id FROM learning_plans WHERE id=?",String.class,plan);
        goal=jdbc.queryForObject("SELECT id FROM plan_goals WHERE plan_id=? LIMIT 1",String.class,plan);
        staff=id();guardian=id();
        jdbc.update("INSERT INTO staff(id,code,user_id,full_name,joined_on) VALUES(?,?,?,'Report teacher','2023-01-01')",staff,"RP-"+staff.substring(0,8),teacher.id());
        assignments.create(new IndividualAssignmentService.Create(student,staff));
        jdbc.update("INSERT INTO guardian(id,code,user_id,full_name) VALUES(?,?,?,'Report guardian')",guardian,"RP-"+guardian.substring(0,8),parent.id());
        jdbc.update("INSERT INTO student_guardian(student_id,guardian_id,relationship,is_primary,can_view_portal) VALUES(?,?,'GUARDIAN',0,1)",student,guardian);
    }
    @Test void multiplePlansGoalsReportsAndPublishedSnapshotRemainIndependent() {
        String anotherPlan=id();
        jdbc.update("INSERT INTO learning_plans(id,student_id,name,start_date,created_by,status) VALUES(?,?,?,'2026-10-01',?,'ACTIVE')",
            anotherPlan,student,"Next plan",admin.id());
        assertTrue(jdbc.queryForObject("SELECT COUNT(*) FROM learning_plans WHERE student_id=?",Integer.class,student)>=2);
        assertTrue(jdbc.queryForObject("SELECT COUNT(*) FROM plan_goals WHERE plan_id=?",Integer.class,plan)>1);
        var first=reports.create(plan,new Input("Tiến bộ tháng đầu"),teacher);
        var second=reports.create(plan,new Input("Tiến bộ tháng tiếp"),teacher);
        assertEquals(2,reports.list(plan,teacher).size());
        assertEquals(0,reports.list(plan,parent).size());
        reports.update(first.id(),new Input("Bản nháp đã sửa"),teacher);
        assertEquals("Bản nháp đã sửa",reports.get(first.id(),teacher).evaluationNotes());
        assertThrows(ResponseStatusException.class,()->reports.update(first.id(),new Input("Người khác sửa"),otherTeacher));
        var published=reports.publish(first.id(),teacher);
        assertTrue(published.sentToParent());assertNotNull(published.sentAt());
        assertEquals("ACTIVE",jdbc.queryForObject("SELECT status FROM learning_plans WHERE id=?",String.class,plan));
        assertEquals(1,reports.list(plan,parent).size());
        assertEquals("Bản nháp đã sửa",reports.get(first.id(),parent).evaluationNotes());
        assertThrows(ResponseStatusException.class,()->reports.update(first.id(),new Input("Overwrite"),teacher));
        assertThrows(ResponseStatusException.class,()->reports.get(second.id(),parent));
        planProgress.edit(plan,new EditPlan("Kế hoạch vẫn đang học",LocalDate.of(2026,10,1),"ACTIVE"),teacher);
        goals.create(plan,goal,new GoalEvaluationDtos.Input(new BigDecimal("90"),null,"Goal changed"),teacher);
        String additionalGoal=jdbc.queryForObject("SELECT g.id FROM goals g WHERE g.is_active=1 AND NOT EXISTS " +
            "(SELECT 1 FROM plan_goals pg WHERE pg.plan_id=? AND pg.goal_id=g.id AND pg.specific_goal_id IS NULL) ORDER BY g.id LIMIT 1",String.class,plan);
        planGoals.assign(plan,new AssignGoal(additionalGoal,null),teacher);
        String replacementGoal=jdbc.queryForObject("SELECT g.id FROM goals g WHERE g.is_active=1 AND g.id<>? AND NOT EXISTS " +
            "(SELECT 1 FROM plan_goals pg WHERE pg.plan_id=? AND pg.goal_id=g.id AND pg.specific_goal_id IS NULL) ORDER BY g.id LIMIT 1",
            String.class,additionalGoal,plan);
        var replacement=planGoals.replace(plan,goal,new AssignGoal(replacementGoal,null),teacher);
        assertEquals(goal,jdbc.queryForObject("SELECT supersedes_id FROM plan_goals WHERE id=?",String.class,replacement.id()));
        assertTrue(planGoals.list(plan,teacher).stream().noneMatch(item->item.id().equals(goal)));
        assertTrue(planGoals.list(plan,teacher).stream().anyMatch(item->item.id().equals(replacement.id())));
        assertFalse(goals.list(plan,goal,teacher).isEmpty());
        assertEquals("Bản nháp đã sửa",reports.get(first.id(),parent).evaluationNotes());
        assertEquals("ACTIVE",jdbc.queryForObject("SELECT status FROM learning_plans WHERE id=?",String.class,plan));
        assertEquals(2,reports.list(plan,teacher).size());
    }
    @Test void parentCannotReadDraftOrEditAndUnassignedTeacherCannotRead() throws Exception {
        var draft=reports.create(plan,new Input("Nhận xét nháp"),teacher);
        String list="/api/learning-plans/"+plan+"/evaluations",detail="/api/plan-evaluations/"+draft.id();
        mvc.perform(get(list).with(user(parent))).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get(detail).with(user(parent))).andExpect(status().isNotFound());
        mvc.perform(get(list).with(user(otherParent))).andExpect(status().isForbidden());
        mvc.perform(get(list).with(user(otherTeacher))).andExpect(status().isForbidden());
        mvc.perform(put(detail).with(user(parent)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content("{\"evaluationNotes\":\"Changed\"}")).andExpect(status().isForbidden());
        mvc.perform(post(detail+"/send").with(user(teacher))).andExpect(status().isForbidden());
        mvc.perform(post(detail+"/send").with(user(teacher)).with(csrf())).andExpect(status().isOk())
            .andExpect(jsonPath("$.sentToParent").value(true));
        mvc.perform(get(detail).with(user(parent))).andExpect(status().isOk()).andExpect(jsonPath("$.evaluationNotes").value("Nhận xét nháp"));
        mvc.perform(get("/api/parent/plan-evaluations").with(user(parent))).andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1));
        mvc.perform(get("/api/parent/plan-evaluations").with(user(otherParent))).andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(0));
        jdbc.update("UPDATE student_guardian SET can_view_portal=0 WHERE student_id=? AND guardian_id=?",student,guardian);
        mvc.perform(get(detail).with(user(parent))).andExpect(status().isForbidden());
        mvc.perform(get("/api/parent/plan-evaluations").with(user(parent))).andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(0));
    }
    @Test void databaseRequiresConsistentPublicationAndProtectsPublishedText() {
        var draft=reports.create(plan,new Input("Nội dung lưu tại thời điểm tạo"),teacher);
        assertThrows(DataAccessException.class,()->jdbc.update("UPDATE plan_evaluations SET sent_to_parent=1 WHERE id=?",draft.id()));
        reports.publish(draft.id(),teacher);
        assertThrows(DataAccessException.class,()->jdbc.update("UPDATE plan_evaluations SET evaluation_notes='changed' WHERE id=?",draft.id()));
        assertThrows(DataAccessException.class,()->jdbc.update("DELETE FROM plan_evaluations WHERE id=?",draft.id()));
        assertEquals("Nội dung lưu tại thời điểm tạo",reports.get(draft.id(),parent).evaluationNotes());
    }
    @Test void publicationRequiresAnAccessibleParentAccount() {
        String unlinkedStudent=id(),unlinkedPlan=id();
        jdbc.update("INSERT INTO student(id,code,full_name,birth_date,admitted_on) VALUES(?,?,'Unlinked student','2022-01-01','2023-01-01')",
            unlinkedStudent,"RP-"+unlinkedStudent.substring(0,8));
        jdbc.update("INSERT INTO learning_plans(id,student_id,name,start_date,status,created_by) VALUES(?,?,?,'2026-10-01','ACTIVE',?)",
            unlinkedPlan,unlinkedStudent,"Unlinked plan",admin.id());
        assignments.create(new IndividualAssignmentService.Create(unlinkedStudent,staff));
        var draft=reports.create(unlinkedPlan,new Input("Nhận xét chưa công bố"),teacher);
        assertThrows(ResponseStatusException.class,()->reports.publish(draft.id(),teacher));
        assertFalse(reports.get(draft.id(),teacher).sentToParent());
        assertNull(reports.get(draft.id(),teacher).sentAt());
    }
    @Test void requiresWholePlanEvaluationBeforeCompletionAndRecordsEndDate() throws Exception {
        String endpoint="/api/plans/"+plan+"/status";
        mvc.perform(patch(endpoint).with(user(otherTeacher)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content("{\"status\":\"PAUSED\"}")).andExpect(status().isForbidden());
        mvc.perform(patch(endpoint).with(user(parent)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content("{\"status\":\"PAUSED\"}")).andExpect(status().isForbidden());
        mvc.perform(patch(endpoint).with(user(teacher)).contentType(MediaType.APPLICATION_JSON)
            .content("{\"status\":\"PAUSED\"}")).andExpect(status().isForbidden());
        mvc.perform(patch(endpoint).with(user(teacher)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content("{\"status\":\"COMPLETED\"}")).andExpect(status().isConflict());
        mvc.perform(patch("/api/plans/"+plan).with(user(teacher)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content("{\"name\":\"Must evaluate first\",\"startDate\":\"2026-10-01\",\"status\":\"COMPLETED\"}"))
            .andExpect(status().isConflict());
        jdbc.update("UPDATE learning_plans SET end_date='2027-12-31' WHERE id=?",plan);
        mvc.perform(patch("/api/plans/"+plan).with(user(teacher)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content("{\"name\":\"History test\",\"startDate\":\"2026-10-01\",\"status\":\"ACTIVE\"}"))
            .andExpect(status().isNoContent());
        assertEquals(LocalDate.of(2027,12,31),jdbc.queryForObject("SELECT end_date FROM learning_plans WHERE id=?",LocalDate.class,plan));
        mvc.perform(patch(endpoint).with(user(teacher)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content("{\"status\":\"PAUSED\"}")).andExpect(status().isNoContent());
        assertEquals("PAUSED",jdbc.queryForObject("SELECT status FROM learning_plans WHERE id=?",String.class,plan));
        var draft=reports.create(plan,new Input("Đánh giá cả kế hoạch trước khi hoàn thành"),teacher);
        jdbc.update("UPDATE learning_plans SET end_date='2027-12-31' WHERE id=?",plan);
        mvc.perform(patch(endpoint).with(user(teacher)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content("{\"status\":\"COMPLETED\"}")).andExpect(status().isNoContent());
        var completed=workflow.workspace(student).plans().stream().filter(p->p.plan().id().equals(plan)).findFirst().orElseThrow().plan();
        assertEquals("COMPLETED",completed.status());assertTrue(completed.hasEvaluation());assertFalse(completed.sentToParent());
        assertNotNull(completed.createdAt());
        assertEquals(LocalDate.now(java.time.ZoneId.of("Asia/Ho_Chi_Minh")),completed.endDate());
        assertEquals("Đánh giá cả kế hoạch trước khi hoàn thành",reports.get(draft.id(),teacher).evaluationNotes());
        mvc.perform(patch(endpoint).with(user(teacher)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content("{\"status\":\"PAUSED\"}")).andExpect(status().isConflict());
    }
}

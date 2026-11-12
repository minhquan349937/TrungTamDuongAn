package com.example.TTGDHNDuongAn.catalog;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.example.TTGDHNDuongAn.dto.PlanGoalDtos.AssignGoal;
import com.example.TTGDHNDuongAn.repository.ShortIds;
import com.example.TTGDHNDuongAn.security.AppUserPrincipal;
import com.example.TTGDHNDuongAn.service.*;
import java.math.BigDecimal;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties={"spring.datasource.url=${AI_SUMMARY_TEST_DB_URL}","SETUP_ADMIN_EMAIL=","SETUP_ADMIN_PASSWORD=",
    "spring.docker.compose.enabled=false","logging.level.root=WARN","debug=false"})
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named="AI_SUMMARY_TEST_DB_URL",matches=".+")
@Transactional
class AiSummaryMysqlTests {
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @Autowired AiSummaryDataService data;
    @Autowired PlanGoalService goals;
    @Autowired IndividualAssignmentService assignments;
    @MockitoBean GeminiSummaryClient client;
    AppUserPrincipal admin;
    String plan,student;
    String next(String table) { return ShortIds.next(jdbc,table); }
    @BeforeEach void setup() {
        String user=next("app_user");
        jdbc.update("INSERT INTO app_user(id,email,password_hash,display_name,role) VALUES(?,?,'unused','AI synthetic admin','ADMIN')",user,"ai-"+user+"@test.invalid");
        admin=new AppUserPrincipal(user,"ai-"+user+"@test.invalid","AI synthetic admin","ADMIN","unused");
        student=next("student");
        jdbc.update("INSERT INTO student(id,code,full_name,birth_date,admitted_on) VALUES(?,?,'AI SYNTHETIC CHILD','2022-01-01','2023-01-01')",student,"AI-"+student.substring(0,8));
        plan=next("learning_plans");
        jdbc.update("INSERT INTO learning_plans(id,student_id,name,start_date,created_by,status) VALUES(?,?,'AI SYNTHETIC PLAN','2026-10-01',?,'ACTIVE')",plan,student,admin.id());
    }
    @Test void mysqlSnapshotGenerationAndTeacherEditedSaveDoNotChangeGoalDataOrPlanStatus() throws Exception {
        var targets=jdbc.queryForList("SELECT id FROM goals WHERE is_active=1 ORDER BY id LIMIT 4",String.class);
        assertEquals(4,targets.size(),"Test needs four active catalog goals");
        String first=goals.assign(plan,new AssignGoal(targets.get(0),null),admin).id();
        String replace=goals.assign(plan,new AssignGoal(targets.get(1),null),admin).id();
        String low=goals.assign(plan,new AssignGoal(targets.get(2),null),admin).id();
        String replacement=goals.replace(plan,replace,new AssignGoal(targets.get(3),null),admin).id();
        evaluation(first,1,"20");evaluation(first,2,"80");evaluation(low,1,"79.99");
        var before=jdbc.queryForList("SELECT * FROM goal_evaluations WHERE plan_goal_id IN (?,?) ORDER BY plan_goal_id,attempt_no",first,low);
        var context=data.load(plan,admin);
        assertEquals(3,context.goals().size());
        assertEquals(1,context.goals().stream().filter(g->g.status().equals("ACHIEVED")).count());
        assertEquals(1,context.goals().stream().filter(g->g.status().equals("NOT_ACHIEVED")).count());
        assertEquals(1,context.goals().stream().filter(g->g.status().equals("NOT_STARTED")).count());
        assertNotEquals(replace,replacement);
        when(client.generate(any(),anyString())).thenReturn("Nhận xét AI giả lập");
        mvc.perform(post("/api/learning-plans/"+plan+"/ai-summary").with(user(admin)).with(csrf())
            .contentType(MediaType.APPLICATION_JSON).content("{\"keywords\":\"Hợp tác tốt\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.generatedText").value("Nhận xét AI giả lập"));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM plan_evaluations WHERE learning_plan_id=?",Integer.class,plan));
        mvc.perform(post("/api/learning-plans/"+plan+"/evaluations").with(user(admin)).with(csrf())
            .contentType(MediaType.APPLICATION_JSON).content("{\"evaluationNotes\":\"Giáo viên đã chỉnh sửa gợi ý AI\"}"))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.evaluationNotes").value("Giáo viên đã chỉnh sửa gợi ý AI"));
        assertEquals(before,jdbc.queryForList("SELECT * FROM goal_evaluations WHERE plan_goal_id IN (?,?) ORDER BY plan_goal_id,attempt_no",first,low));
        assertEquals("ACTIVE",jdbc.queryForObject("SELECT status FROM learning_plans WHERE id=?",String.class,plan));
    }
    void evaluation(String goal,int attempt,String percent) {
        jdbc.update("INSERT INTO goal_evaluations(id,plan_goal_id,attempt_no,achievement_percent,evaluated_at,evaluated_by) VALUES(?,?,?,?,UTC_TIMESTAMP(6),?)",
            next("goal_evaluations"),goal,attempt,new BigDecimal(percent),admin.id());
    }
    @Test void onlyAssignedActiveTeacherCanGenerateForAnUnevaluatedPlan() throws Exception {
        String teacherId=next("app_user"), staffId=next("staff");
        jdbc.update("INSERT INTO app_user(id,email,password_hash,display_name,role) VALUES(?,?,'unused','AI synthetic teacher','TEACHER')",teacherId,"ai-"+teacherId+"@test.invalid");
        jdbc.update("INSERT INTO staff(id,code,user_id,full_name,joined_on) VALUES(?,?,?,'AI synthetic teacher','2023-01-01')",staffId,"AI-"+staffId.substring(0,8),teacherId);
        var teacher=new AppUserPrincipal(teacherId,"ai-"+teacherId+"@test.invalid","AI synthetic teacher","TEACHER","unused");
        String url="/api/learning-plans/"+plan+"/ai-summary";
        mvc.perform(post(url).with(user(teacher)).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isForbidden());
        verifyNoInteractions(client);
        assignments.create(new IndividualAssignmentService.Create(student,staffId));
        assertTrue(data.load(plan,teacher).goals().isEmpty());
        when(client.generate(any(),eq(""))).thenReturn("Chưa có dữ liệu đánh giá mục tiêu.");
        mvc.perform(post(url).with(user(teacher)).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.generatedText").value("Chưa có dữ liệu đánh giá mục tiêu."));
        jdbc.update("UPDATE app_user SET status='DISABLED' WHERE id=?",teacherId);
        mvc.perform(post(url).with(user(teacher)).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isForbidden());
        verify(client,times(1)).generate(any(),anyString());
    }
}

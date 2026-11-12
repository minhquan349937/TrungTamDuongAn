package com.example.TTGDHNDuongAn.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.example.TTGDHNDuongAn.security.AppUserPrincipal;
import java.math.BigDecimal;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.*;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

class AiSummaryDataTests {
    EmbeddedDatabase db;
    JdbcTemplate jdbc;
    PlanAccessService access;
    AiSummaryDataService service;
    AppUserPrincipal admin=new AppUserPrincipal("TK000001","test@invalid.test","Test","ADMIN","unused");
    @BeforeEach void setup() {
        db=new EmbeddedDatabaseBuilder().generateUniqueName(true).setType(EmbeddedDatabaseType.H2).build();
        jdbc=new JdbcTemplate(db);access=mock(PlanAccessService.class);service=new AiSummaryDataService(jdbc,access);
        jdbc.execute("CREATE TABLE learning_plans(id VARCHAR PRIMARY KEY,student_id VARCHAR,name VARCHAR,status VARCHAR)");
        jdbc.execute("CREATE TABLE goals(id VARCHAR PRIMARY KEY,name VARCHAR,description VARCHAR)");
        jdbc.execute("CREATE TABLE specific_goals(id VARCHAR PRIMARY KEY,description VARCHAR)");
        jdbc.execute("CREATE TABLE plan_goals(id VARCHAR PRIMARY KEY,plan_id VARCHAR,goal_id VARCHAR,specific_goal_id VARCHAR,supersedes_id VARCHAR,assigned_at INT)");
        jdbc.execute("CREATE TABLE goal_evaluations(plan_goal_id VARCHAR,attempt_no INT,achievement_percent DECIMAL(5,2),result_status VARCHAR,evaluation_notes VARCHAR)");
        jdbc.update("INSERT INTO learning_plans VALUES('KH000001','PRIVATE-STUDENT','PRIVATE-PLAN','ACTIVE'),('KH000002','OTHER-STUDENT','OTHER-PLAN','ACTIVE')");
        jdbc.update("INSERT INTO goals VALUES('g1','Giao tiếp','Mô tả chung'),('g2','Tập trung',NULL)");
        jdbc.update("INSERT INTO specific_goals VALUES('s1','Mô tả mục tiêu cụ thể')");
    }
    @AfterEach void close() { db.shutdown(); }
    void goal(String id,String plan,String target,String specific,String previous) {
        jdbc.update("INSERT INTO plan_goals VALUES(?,?,?,?,?,0)",id,plan,target,specific,previous);
    }
    void evaluation(String id,int attempt,String percent,String status) {
        jdbc.update("INSERT INTO goal_evaluations VALUES(?,?,?,?,?)",id,attempt,percent==null?null:new BigDecimal(percent),status,"Ghi nhận giả lập");
    }
    @Test void latestAttemptsReplacementsAndOtherPlansAreHandledWithoutWrites() {
        goal("old","KH000001","g1",null,null);evaluation("old",1,"100","ACHIEVED");
        goal("middle","KH000001","g2",null,"old");
        goal("current","KH000001","g1","s1","middle");evaluation("current",1,"20","NOT_ACHIEVED");evaluation("current",2,"80","ACHIEVED");
        goal("low","KH000001","g2",null,null);evaluation("low",1,"79.99","NOT_ACHIEVED");
        goal("not-started","KH000001","g2",null,null);evaluation("not-started",1,"90","ACHIEVED");evaluation("not-started",2,null,"NOT_STARTED");
        goal("missing","KH000001","g1",null,null);
        goal("other","KH000002","g2",null,null);evaluation("other",1,"100","ACHIEVED");
        var before=jdbc.queryForList("SELECT * FROM goal_evaluations ORDER BY plan_goal_id,attempt_no");
        var context=service.load("KH000001",admin);
        assertEquals(4,context.goals().size());
        var current=context.goals().stream().filter(g->"Mô tả mục tiêu cụ thể".equals(g.description())).findFirst().orElseThrow();
        assertEquals(new BigDecimal("80.00"),current.achievementPercent());
        assertEquals(1,context.goals().stream().filter(g->g.status().equals("ACHIEVED")).count());
        assertEquals(1,context.goals().stream().filter(g->g.status().equals("NOT_ACHIEVED")).count());
        assertEquals(2,context.goals().stream().filter(g->g.status().equals("NOT_STARTED")).count());
        String payload=new ObjectMapper().writeValueAsString(context);
        assertFalse(payload.contains("PRIVATE"));assertFalse(payload.contains("KH000001"));
        assertEquals(before,jdbc.queryForList("SELECT * FROM goal_evaluations ORDER BY plan_goal_id,attempt_no"));
        assertEquals("ACTIVE",jdbc.queryForObject("SELECT status FROM learning_plans WHERE id='KH000001'",String.class));
    }
    @Test void emptyPlanAndGoalsWithoutEvaluationsRemainExplicit() {
        assertTrue(service.load("KH000001",admin).goals().isEmpty());
        goal("empty","KH000001","g1",null,null);
        assertEquals("NOT_STARTED",service.load("KH000001",admin).goals().getFirst().status());
        assertNull(service.load("KH000001",admin).goals().getFirst().achievementPercent());
    }
    @Test void unassignedTeacherAndParentAreDenied() {
        var teacher=new AppUserPrincipal("TK000002","teacher@invalid.test","Test","TEACHER","unused");
        doThrow(new ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN)).when(access).requireStudent("PRIVATE-STUDENT",teacher);
        assertEquals(403,assertThrows(ResponseStatusException.class,()->service.load("KH000001",teacher)).getStatusCode().value());
        var parent=new AppUserPrincipal("TK000003","parent@invalid.test","Test","PARENT","unused");
        assertEquals(403,assertThrows(ResponseStatusException.class,()->service.load("KH000001",parent)).getStatusCode().value());
        assertEquals(404,assertThrows(ResponseStatusException.class,()->service.load("KH000099",admin)).getStatusCode().value());
    }
}

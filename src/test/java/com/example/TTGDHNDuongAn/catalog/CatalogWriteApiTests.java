package com.example.TTGDHNDuongAn.catalog;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import com.example.TTGDHNDuongAn.dto.GoalCatalogDtos.*;
import com.example.TTGDHNDuongAn.security.AppUserPrincipal;
import com.example.TTGDHNDuongAn.service.GoalCatalogService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataAccessException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(properties = "spring.datasource.url=${GOAL_CATALOG_TEST_DB_URL}")
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "GOAL_CATALOG_TEST_DB_URL", matches = ".+")
class CatalogWriteApiTests {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired GoalCatalogService service;
    private final ObjectMapper mapper = new ObjectMapper();

    private JsonNode create(String path, String body) throws Exception {
        var result = mvc.perform(post(path).with(user("admin").roles("ADMIN")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn();
        return mapper.readTree(result.getResponse().getContentAsString());
    }

    @Test @Transactional
    void createsBothProgramsLevelsDomainsAndFourSpecificGoalsUsingSharedApi() throws Exception {
        String denver = create("/api/programs", """
                {"code":"WRITE-DENVER","name":"Denver test"}
                """).get("id").asText();
        String vb = create("/api/programs", """
                {"code":"WRITE-VB","name":"VB test"}
                """).get("id").asText();
        assertTrue(denver.matches("PR[0-9]{4,6}"));
        assertNotEquals(denver, vb);
        List<String> domains = new ArrayList<>();
        for (String program : List.of(denver, vb)) {
            String level = create("/api/programs/" + program + "/levels", """
                    {"levelNumber":1,"name":"Test level","ageMinMonths":12,"ageMaxMonths":48,"displayOrder":1}
                    """).get("id").asText();
            assertTrue(level.startsWith("LV"));
            String domain = create("/api/levels/" + level + "/domains", """
                    {"code":"SAME","name":"Test domain","displayOrder":1}
                    """).get("levelDomainId").asText();
            assertTrue(domain.startsWith("LD")); domains.add(domain);
            mvc.perform(post("/api/programs/" + program + "/levels").with(user("admin").roles("ADMIN")).with(csrf())
                            .contentType(MediaType.APPLICATION_JSON).content("""
                                    {"levelNumber":1,"name":"Duplicate","ageMinMonths":12,"ageMaxMonths":48,"displayOrder":1}
                                    """))
                    .andExpect(status().isConflict());
        }
        String denverGoal = create("/api/level-domains/" + domains.getFirst() + "/goals", """
                {"code":"SAME.1","name":"Unabridged test name","description":"Full test description","displayOrder":1}
                """).get("id").asText();
        JsonNode vbGoal = create("/api/level-domains/" + domains.getLast() + "/goals", """
                {"code":"SAME.1","name":"General test goal","description":null,"displayOrder":1}
                """);
        String vbId = vbGoal.get("id").asText();
        assertTrue(denverGoal.matches("GL[0-9]{6}"));
        assertTrue(vbGoal.get("description").isNull());
        assertEquals(0, vbGoal.get("specificGoals").size());
        JsonNode detail = vbGoal;
        for (char code : new char[]{'A','B','C','D'}) {
            detail = create("/api/goals/" + vbId + "/specific-goals",
                    "{\"code\":\"TEST." + code + "\",\"description\":\"Test content " + code + "\",\"displayOrder\":" + (code - 'A') + "}");
        }
        assertEquals(4, detail.get("specificGoals").size());
        assertTrue(detail.get("specificGoals").get(0).get("id").asText().matches("SG[0-9]{6}"));
        assertEquals(12, detail.get("specificGoals").get(0).get("ageMinMonths").asInt());
        mvc.perform(get("/api/goals/" + denverGoal).with(user("parent").roles("PARENT")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.specificGoals").isArray())
                .andExpect(jsonPath("$.description").value("Full test description"));
        create("/api/level-domains/" + domains.getLast() + "/goals", """
                {"code":"NO.CHILD","name":"Test without children","displayOrder":2}
                """);
        mvc.perform(get("/api/level-domains/" + domains.getLast() + "/goals").with(user("teacher").roles("TEACHER"))
                        .param("page", "1").param("size", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].code").value("NO.CHILD"));
        mvc.perform(post("/api/goals/" + vbId + "/specific-goals").with(user("admin").roles("ADMIN")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"code":"TEST.A","description":"Duplicate","displayOrder":1}
                                """))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/goals/" + vbId + "/specific-goals").with(user("admin").roles("ADMIN")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"code":"BAD","description":"Bad age","ageMinMonths":49,"displayOrder":1}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test @Transactional
    void parentChildFkAssignmentsSnapshotsAndProgressSurviveCatalogEditsAndArchival() throws Exception {
        var p = service.createProgram(new CreateProgram("HISTORY-TEST", "Test", null));
        var l = service.createLevel(p.id(), new CreateLevel(1,"Test",0,48,1));
        var d = service.createDomain(l.id(),new CreateDomain("TEST","Test",1));
        var g = service.createGoal(d.levelDomainId(),new CreateGoal("TEST.1","Original","Original description",1));
        var other = service.createGoal(d.levelDomainId(),new CreateGoal("TEST.2","Other",null,2));
        var specific = service.createSpecific(g.id(),new WriteSpecificGoal("TEST.1A","Original child",null,null,1)).specificGoals().getFirst();
        String actor=UUID.randomUUID().toString(), student=UUID.randomUUID().toString(), plan=UUID.randomUUID().toString();
        jdbc.update("INSERT INTO app_user(id,email,password_hash,display_name,role) VALUES(?,?,'unused','Test','ADMIN')", actor,actor+"@example.invalid");
        jdbc.update("INSERT INTO student(id,code,full_name,birth_date,admitted_on) VALUES(?,'HISTORY-TEST','Test','2022-01-01','2023-01-01')",student);
        jdbc.update("INSERT INTO learning_plans(id,student_id,name,start_date,created_by) VALUES(?,?,'Test','2026-01-01',?)",plan,student,actor);
        var admin = new AppUserPrincipal(actor,actor+"@example.invalid","Test","ADMIN","unused");
        for (String body : List.of("{\"goalId\":\""+g.id()+"\"}",
                "{\"goalId\":\""+g.id()+"\",\"specificGoalId\":\""+specific.id()+"\"}")) {
            mvc.perform(post("/api/plans/"+plan+"/goals").with(user(admin)).with(csrf())
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isCreated()).andExpect(jsonPath("$.goalId").value(g.id()));
        }
        mvc.perform(post("/api/plans/"+plan+"/goals").with(user(admin)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"goalId\":\""+other.id()+"\",\"specificGoalId\":\""+specific.id()+"\"}"))
                .andExpect(status().isConflict());
        assertThrows(DataAccessException.class, () -> jdbc.update(
                "INSERT INTO plan_goals(id,plan_id,goal_id,specific_goal_id,goal_snapshot,criteria_snapshot,assigned_by) VALUES(?,?,?,?,JSON_OBJECT(),JSON_OBJECT(),?)",
                UUID.randomUUID().toString(),plan,other.id(),specific.id(),actor));
        var original = jdbc.queryForList("SELECT id,goal_snapshot,criteria_snapshot FROM plan_goals WHERE plan_id=? ORDER BY id",plan);
        String assignment=original.getFirst().get("id").toString();
        jdbc.update("INSERT INTO goal_evaluations(id,plan_goal_id,attempt_no,evaluated_at,evaluated_by) VALUES(?,?,1,UTC_TIMESTAMP(6),?)",UUID.randomUUID().toString(),assignment,actor);
        mvc.perform(put("/api/goals/"+g.id()).with(user(admin)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"TEST.1\",\"name\":\"Changed\",\"displayOrder\":1}"))
                .andExpect(status().isOk());
        service.updateSpecific(g.id(),specific.id(),new WriteSpecificGoal("TEST.1A","Changed child",null,null,1));
        assertThrows(DataAccessException.class, () -> jdbc.update("DELETE FROM goals WHERE id=?",g.id()));
        assertThrows(DataAccessException.class, () -> jdbc.update("DELETE FROM specific_goals WHERE id=?",specific.id()));
        mvc.perform(delete("/api/goals/"+g.id()).with(user(admin)).with(csrf())).andExpect(status().isNoContent());
        assertEquals(original,jdbc.queryForList("SELECT id,goal_snapshot,criteria_snapshot FROM plan_goals WHERE plan_id=? ORDER BY id",plan));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM goal_evaluations WHERE plan_goal_id=?",Integer.class,assignment));
        mvc.perform(get("/api/plans/"+plan+"/goals").with(user(admin))).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void simultaneousCreatesAllocateDistinctShortIds() throws Exception {
        String marker="CONCURRENT-"+UUID.randomUUID().toString().substring(0,8);
        try {
            List<Callable<String>> calls=new ArrayList<>();
            for(int n=0;n<24;n++) {
                int number=n;
                calls.add(() -> service.createProgram(new CreateProgram(marker+number,"Test",null)).id());
            }
            var ids=new HashSet<String>();
            try(var pool=Executors.newFixedThreadPool(8)) {
                for(var result:pool.invokeAll(calls)) {
                    String id=result.get(); assertTrue(id.matches("PR[0-9]{4,6}")); assertTrue(ids.add(id));
                }
            }
            assertEquals(24,ids.size());
        } finally {
            jdbc.update("DELETE FROM programs WHERE code LIKE ?",marker+"%");
        }
    }

    @Test
    void allCatalogWritesRequireAdminAndReadsRequireAuthentication() throws Exception {
        for(String role:List.of("TEACHER","PARENT")) {
            mvc.perform(post("/api/programs").with(user("reader").roles(role)).with(csrf())
                            .contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"X\",\"name\":\"X\"}"))
                    .andExpect(status().isForbidden());
            mvc.perform(put("/api/goals/GL000001").with(user("reader").roles(role)).with(csrf())
                            .contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isForbidden());
            mvc.perform(delete("/api/goals/GL000001").with(user("reader").roles(role)).with(csrf()))
                    .andExpect(status().isForbidden());
        }
        mvc.perform(get("/api/programs")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/programs").with(user("admin").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test @Transactional
    void preservesFullTextAndRejectsIdExhaustionWithoutOverflow() {
        var p=service.createProgram(new CreateProgram("TEXT-TEST","Test",null));
        var l=service.createLevel(p.id(),new CreateLevel(1,"Test",0,48,1));
        var d=service.createDomain(l.id(),new CreateDomain("TEXT","Test",1));
        String text="  Unabridged test content. ".repeat(1500);
        var goal=service.createGoal(d.levelDomainId(),new CreateGoal("TEXT.1",text,text,1));
        assertEquals(text,goal.name()); assertEquals(text,goal.description());
        jdbc.update("UPDATE catalog_id_sequences SET next_value=1000000 WHERE entity_name='programs'");
        var error=assertThrows(org.springframework.web.server.ResponseStatusException.class,
                () -> service.createProgram(new CreateProgram("OVERFLOW","Test",null)));
        assertEquals(409,error.getStatusCode().value());
        assertEquals(1000000,jdbc.queryForObject("SELECT next_value FROM catalog_id_sequences WHERE entity_name='programs'",Integer.class));
    }
}

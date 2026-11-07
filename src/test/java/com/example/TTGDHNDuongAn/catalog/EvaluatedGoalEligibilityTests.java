package com.example.TTGDHNDuongAn.catalog;

import com.example.TTGDHNDuongAn.dto.PlanGoalDtos.AssignGoal;
import com.example.TTGDHNDuongAn.repository.PlanGoalRepository;
import com.example.TTGDHNDuongAn.security.AppUserPrincipal;
import com.example.TTGDHNDuongAn.service.PlanGoalService;
import com.example.TTGDHNDuongAn.service.StudentGoalHistoryService;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"spring.datasource.url=${GOAL_ELIGIBILITY_TEST_DB_URL}",
    "SETUP_ADMIN_EMAIL=","SETUP_ADMIN_PASSWORD=","spring.docker.compose.enabled=false",
    "logging.level.root=WARN","debug=false"})
@EnabledIfEnvironmentVariable(named="GOAL_ELIGIBILITY_TEST_DB_URL",matches=".+")
@AutoConfigureMockMvc
@Transactional
class EvaluatedGoalEligibilityTests {
    @Autowired JdbcTemplate jdbc;
    @Autowired PlanGoalRepository repository;
    @Autowired PlanGoalService service;
    @Autowired StudentGoalHistoryService history;
    @Autowired MockMvc mvc;
    AppUserPrincipal actor;
    String student,otherStudent,plan,oldPlan,otherPlan,domain;
    List<String> goals;
    String id() { return UUID.randomUUID().toString(); }
    @BeforeEach void fixture() {
        String user=id();actor=new AppUserPrincipal(user,user+"@eligibility.invalid","Eligibility","ADMIN","unused");
        jdbc.update("INSERT INTO app_user(id,email,password_hash,display_name,role) VALUES(?,?,'unused','Eligibility','ADMIN')",user,actor.email());
        student=id();otherStudent=id();
        for(String s:List.of(student,otherStudent)) jdbc.update("INSERT INTO student(id,code,full_name,birth_date,admitted_on) VALUES(?,?,'Eligibility','2022-01-01','2023-01-01')",s,"EL-"+s.substring(0,8));
        plan=id();oldPlan=id();otherPlan=id();
        for(String p:List.of(plan,oldPlan,otherPlan)) jdbc.update("INSERT INTO learning_plans(id,student_id,name,start_date,created_by) VALUES(?,?,'Eligibility','2026-11-07',?)",p,p.equals(otherPlan)?otherStudent:student,user);
        domain=jdbc.queryForObject("SELECT g.level_domain_id FROM goals g JOIN program_level_domains ld ON ld.id=g.level_domain_id JOIN programs p ON p.id=ld.program_id WHERE p.code IN ('ESDM','DENVER') AND g.is_active=1 GROUP BY g.level_domain_id HAVING COUNT(*)>=3 ORDER BY g.level_domain_id LIMIT 1",String.class);
        goals=jdbc.queryForList("SELECT id FROM goals WHERE level_domain_id=? AND is_active=1 ORDER BY display_order,id LIMIT 3",String.class,domain);
    }
    void evaluate(String row,Integer percentage) {
        jdbc.update("INSERT INTO goal_evaluations(id,plan_goal_id,attempt_no,achievement_percent,evaluated_at,evaluated_by) VALUES(?,?,1,?,IF(? IS NULL,NULL,UTC_TIMESTAMP(6)),?)",id(),row,percentage,percentage,actor.id());
    }
    @Test void hidesAllEvaluatedTargetsBeforePaginationAndRejectsDirectReassignmentOrReplacement() {
        String failed=repository.insert(plan,goals.get(0),null,actor.id());evaluate(failed,60);
        String unscored=repository.insert(oldPlan,goals.get(1),null,actor.id());evaluate(unscored,null);
        jdbc.update("UPDATE learning_plans SET status='CANCELLED' WHERE id=?",oldPlan);
        String unassessed=repository.insert(plan,goals.get(2),null,actor.id());
        var page=service.availableGoals(plan,domain,"",0,1,actor);
        assertEquals(goals.get(2),page.content().getFirst().id());
        long total=jdbc.queryForObject("SELECT COUNT(*) FROM goals WHERE level_domain_id=? AND is_active=1",Long.class,domain)-2;
        assertEquals(total,page.totalElements());assertEquals(total,page.totalPages());
        for(String goal:goals.subList(0,2)) {
            assertEquals(409,assertThrows(ResponseStatusException.class,()->service.assign(plan,new AssignGoal(goal,null),actor)).getStatusCode().value());
            assertEquals(409,assertThrows(ResponseStatusException.class,()->service.availableGoal(plan,goal,actor)).getStatusCode().value());
        }
        assertEquals(409,assertThrows(ResponseStatusException.class,()->service.replace(plan,failed,new AssignGoal(goals.get(2),null),actor)).getStatusCode().value());
        assertTrue(service.list(plan,actor).stream().filter(g->g.id().equals(failed)).findFirst().orElseThrow().hasEvaluations());
        assertFalse(service.list(plan,actor).stream().filter(g->g.id().equals(unassessed)).findFirst().orElseThrow().hasEvaluations());
        assertTrue(history.list(student).stream().filter(h->h.goalId().equals(goals.get(1))).findFirst().orElseThrow().hasEvaluations());
        assertNotNull(service.assign(otherPlan,new AssignGoal(goals.get(0),null),actor));
    }
    @Test void allowsReplacingUnassessedTargetsAndFiltersSpecificGoalsIndependently() {
        String original=repository.insert(plan,goals.get(0),null,actor.id());
        var replacement=service.replace(plan,original,new AssignGoal(goals.get(1),null),actor);
        assertEquals(goals.get(1),replacement.goalId());
        String parent=jdbc.queryForObject("SELECT g.id FROM goals g JOIN specific_goals sg ON sg.goal_id=g.id JOIN program_level_domains ld ON ld.id=g.level_domain_id JOIN programs p ON p.id=ld.program_id WHERE p.code='VB' AND g.is_active=1 AND sg.is_active=1 GROUP BY g.id HAVING COUNT(*)>=2 ORDER BY g.id LIMIT 1",String.class);
        var children=jdbc.queryForList("SELECT id FROM specific_goals WHERE goal_id=? AND is_active=1 ORDER BY display_order,id LIMIT 2",String.class,parent);
        evaluate(repository.insert(oldPlan,parent,null,actor.id()),0);
        evaluate(repository.insert(oldPlan,parent,children.get(0),actor.id()),100);
        var detail=service.availableGoal(plan,parent,actor);
        assertFalse(detail.canSelectGeneralGoal());
        assertTrue(detail.specificGoals().stream().noneMatch(g->g.id().equals(children.get(0))));
        assertTrue(detail.specificGoals().stream().anyMatch(g->g.id().equals(children.get(1))));
        assertEquals(409,assertThrows(ResponseStatusException.class,()->service.assign(plan,new AssignGoal(parent,children.get(0)),actor)).getStatusCode().value());
        assertNotNull(service.assign(plan,new AssignGoal(parent,children.get(1)),actor));
    }
    @Test void apiReportsEvaluationAndRejectsStaleOrDirectReplacementRequests() throws Exception {
        String row=repository.insert(plan,goals.get(0),null,actor.id());
        // A stored evaluation with no percentage is still an evaluation record.
        evaluate(row,null);
        mvc.perform(get("/api/plans/"+plan+"/goals").with(user(actor)))
            .andExpect(status().isOk()).andExpect(jsonPath("$[0].hasEvaluations").value(true));
        mvc.perform(get("/api/plans/"+plan+"/available-goals").param("levelDomainId",domain).with(user(actor)))
            .andExpect(status().isOk());
        mvc.perform(get("/api/plans/"+plan+"/available-goals").param("levelDomainId",domain).with(user("parent").roles("PARENT")))
            .andExpect(status().isForbidden());
        mvc.perform(put("/api/plans/"+plan+"/goals/"+row+"/replace").with(user(actor)).with(csrf())
            .contentType("application/json").content("{\"goalId\":\""+goals.get(1)+"\",\"specificGoalId\":null}"))
            .andExpect(status().isConflict());
        mvc.perform(post("/api/plans/"+oldPlan+"/goals").with(user(actor)).with(csrf())
            .contentType("application/json").content("{\"goalId\":\""+goals.get(0)+"\",\"specificGoalId\":null}"))
            .andExpect(status().isConflict());
    }
}

package com.example.TTGDHNDuongAn.catalog;

import com.example.TTGDHNDuongAn.dto.GoalSelectionDtos.*;
import com.example.TTGDHNDuongAn.dto.InterventionDtos.*;
import com.example.TTGDHNDuongAn.service.InterventionService;
import com.example.TTGDHNDuongAn.service.StrategyService;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.dao.DataIntegrityViolationException;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties={"spring.datasource.url=${STRATEGY_TEST_DB_URL}",
    "SETUP_ADMIN_EMAIL=","SETUP_ADMIN_PASSWORD=","spring.docker.compose.enabled=false",
    "logging.level.root=WARN","debug=false"})
@EnabledIfEnvironmentVariable(named="STRATEGY_TEST_DB_URL",matches=".+")
@Transactional
class StrategyWorkflowTests {
    @Autowired JdbcTemplate jdbc;
    @Autowired InterventionService workflow;
    @Autowired com.example.TTGDHNDuongAn.service.PlanGoalService planGoals;
    @MockitoSpyBean StrategyService strategies;
    String actor,student,program;
    List<String> goals;

    @BeforeEach void fixture() {
        actor=UUID.randomUUID().toString();student=UUID.randomUUID().toString();
        jdbc.update("INSERT INTO app_user(id,email,password_hash,display_name,role) VALUES(?,?,'unused','Strategy test','ADMIN')",actor,actor+"@strategy.invalid");
        jdbc.update("INSERT INTO student(id,code,full_name,birth_date,admitted_on) VALUES(?,?,'Strategy student','2022-01-01','2023-01-01')",student,"ST-"+student.substring(0,8));
        jdbc.update("INSERT INTO strategies(id,name,display_order) VALUES('CL990001','Test model',10),('CL990002','Test wait',20)");
        jdbc.update("INSERT INTO strategies(id,name,is_active) VALUES('CL990003','Inactive',0)");
        program=jdbc.queryForObject("SELECT id FROM programs WHERE code IN ('ESDM','DENVER') ORDER BY id LIMIT 1",String.class);
        goals=jdbc.queryForList("SELECT g.id FROM goals g JOIN program_level_domains ld ON ld.id=g.level_domain_id WHERE ld.program_id=? AND g.is_active=1 ORDER BY g.id LIMIT 2",String.class,program);
    }
    Draft draft(List<Target> targets) {
        return new Draft(UUID.randomUUID().toString(),program,"Strategy plan",LocalDate.of(2026,11,7),targets);
    }
    Complete complete(Review review,List<Target> targets) {
        return new Complete(review.requestId(),review.programId(),review.name(),review.startDate(),targets,review.reviewToken());
    }
    @Test void addingAndReplacingPlanGoalsAcceptOptionalStrategiesAndNormalizeBlankDescriptions() {
        var targetList=List.of(new Target(goals.get(0),null));
        var review=workflow.preview(student,draft(targetList));
        var plan=workflow.complete(student,complete(review,targetList),actor).result().plan().id();
        var principal=new com.example.TTGDHNDuongAn.security.AppUserPrincipal(actor,actor+"@strategy.invalid","Strategy test","ADMIN","unused");
        var empty=planGoals.assign(plan,new com.example.TTGDHNDuongAn.dto.PlanGoalDtos.AssignGoal(goals.get(1),null,null),principal);
        assertTrue(empty.strategies().isEmpty());
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM plan_goal_strategies WHERE plan_goal_id=?",Integer.class,empty.id()));
        String third=jdbc.queryForObject("SELECT id FROM goals WHERE is_active=1 AND id NOT IN (?,?) ORDER BY id LIMIT 1",String.class,goals.get(0),goals.get(1));
        var replacement=planGoals.replace(plan,empty.id(),new com.example.TTGDHNDuongAn.dto.PlanGoalDtos.AssignGoal(third,null,
            List.of(new StrategyInput("CL990001"," \t "))),principal);
        assertEquals(1,replacement.strategies().size());
        assertNull(replacement.strategies().getFirst().description());
        var another=planGoals.assign(plan,new com.example.TTGDHNDuongAn.dto.PlanGoalDtos.AssignGoal(
            jdbc.queryForObject("SELECT id FROM goals WHERE is_active=1 AND id NOT IN (?,?,?) ORDER BY id LIMIT 1",String.class,goals.get(0),goals.get(1),third),null,
            List.of(new StrategyInput("CL990002","Teacher waits for the response."))),principal);
        assertEquals("Teacher waits for the response.",another.strategies().getFirst().description());
    }
    @Test void savesIndependentStrategiesAndDescriptionsForEachTargetAndReplaysWithoutDuplicates() {
        var targets=List.of(
            new Target(goals.get(0),null,List.of(new StrategyInput("CL990002","  "),new StrategyInput("CL990001","  Teacher models the task.  "))),
            new Target(goals.get(1),null,List.of(new StrategyInput("CL990001","A different application."))));
        var review=workflow.preview(student,draft(targets));
        assertEquals("CL990001",review.items().getFirst().strategies().getFirst().strategyId());
        assertEquals("Teacher models the task.",review.items().getFirst().strategies().getFirst().description());
        assertNull(review.items().getFirst().strategies().get(1).description());
        var saved=workflow.complete(student,complete(review,targets),actor);
        assertFalse(saved.alreadySaved());
        assertEquals(2,saved.result().goals().getFirst().strategies().size());
        assertEquals(3,jdbc.queryForObject("SELECT COUNT(*) FROM plan_goal_strategies s JOIN plan_goals g ON g.id=s.plan_goal_id WHERE g.plan_id=?",Integer.class,saved.result().plan().id()));
        assertTrue(workflow.complete(student,complete(review,targets),actor).alreadySaved());
        assertEquals(2,workflow.workspace(student).plans().getFirst().goals().getFirst().strategies().size());
        var changed=List.of(new Target(goals.get(0),null,List.of()),targets.get(1));
        assertEquals(409,assertThrows(ResponseStatusException.class,()->workflow.complete(student,complete(review,changed),actor)).getStatusCode().value());
    }
    @Test void rejectsUnreviewedStrategyEditsAndInactiveOrDuplicateSelections() {
        var target=new Target(goals.getFirst(),null,List.of(new StrategyInput("CL990001",null)));
        var review=workflow.preview(student,draft(List.of(target)));
        var changed=List.of(new Target(goals.getFirst(),null,List.of(new StrategyInput("CL990002",null))));
        assertEquals(409,assertThrows(ResponseStatusException.class,()->workflow.complete(student,complete(review,changed),actor)).getStatusCode().value());
        for(var choices:List.of(List.of(new StrategyInput("CL990003",null)),
                List.of(new StrategyInput("CL990001",null),new StrategyInput("CL990001",null)))) {
            assertEquals(400,assertThrows(ResponseStatusException.class,()->workflow.preview(student,draft(List.of(new Target(goals.getFirst(),null,choices))))).getStatusCode().value());
        }
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM learning_plans WHERE student_id=?",Integer.class,student));
        assertTrue(strategies.active().stream().noneMatch(s->s.id().equals("CL990003")));
    }
    @Test @Transactional(propagation=Propagation.NOT_SUPPORTED)
    void rollsBackPlanAndAllGoalsWhenSavingAStrategyFails() {
        try {
            var targets=List.of(
                new Target(goals.get(0),null,List.of(new StrategyInput("CL990001",null))),
                new Target(goals.get(1),null,List.of(new StrategyInput("CL990002",null))));
            var review=workflow.preview(student,draft(targets));
            doThrow(new DataIntegrityViolationException("Simulated strategy insert failure"))
                .when(strategies).save(anyString(),argThat(choices->choices!=null && choices.stream().anyMatch(s->s.strategyId().equals("CL990002"))));
            assertThrows(DataIntegrityViolationException.class,()->workflow.complete(student,complete(review,targets),actor));
            assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM learning_plans WHERE student_id=?",Integer.class,student));
            assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM plan_goals WHERE assigned_by=?",Integer.class,actor));
            assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM plan_goal_strategies WHERE strategy_id IN ('CL990001','CL990002')",Integer.class));
        } finally {
            reset(strategies);
            jdbc.update("DELETE s FROM plan_goal_strategies s JOIN plan_goals g ON g.id=s.plan_goal_id WHERE g.assigned_by=?",actor);
            jdbc.update("DELETE FROM plan_goals WHERE assigned_by=?",actor);
            jdbc.update("DELETE FROM learning_plans WHERE student_id=?",student);
            jdbc.update("DELETE FROM student WHERE id=?",student);
            jdbc.update("DELETE FROM app_user WHERE id=?",actor);
            jdbc.update("DELETE FROM strategies WHERE id IN ('CL990001','CL990002','CL990003')");
        }
    }
}

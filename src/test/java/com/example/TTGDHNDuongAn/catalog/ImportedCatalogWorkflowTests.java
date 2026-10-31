package com.example.TTGDHNDuongAn.catalog;

import static org.junit.jupiter.api.Assertions.*;
import com.example.TTGDHNDuongAn.dto.GoalSelectionDtos.Target;
import com.example.TTGDHNDuongAn.dto.InterventionDtos.*;
import com.example.TTGDHNDuongAn.dto.PlanProgressDtos.*;
import com.example.TTGDHNDuongAn.service.GoalCatalogService;
import com.example.TTGDHNDuongAn.service.InterventionService;
import com.example.TTGDHNDuongAn.service.GoalEvaluationService;
import com.example.TTGDHNDuongAn.dto.GoalEvaluationDtos.Input;
import com.example.TTGDHNDuongAn.security.AppUserPrincipal;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/** Opt-in verification against imported catalogs. Only temporary student/plan rows
 * are written, inside a test transaction that always rolls back. No catalog reset. */
@SpringBootTest(properties={"spring.datasource.url=${GOAL_CATALOG_IMPORTED_TEST_DB_URL}",
    "SETUP_ADMIN_EMAIL=","SETUP_ADMIN_PASSWORD=","spring.docker.compose.enabled=false",
    "logging.level.root=WARN","debug=false"})
@EnabledIfEnvironmentVariable(named="GOAL_CATALOG_IMPORTED_TEST_DB_URL",matches=".+")
class ImportedCatalogWorkflowTests {
    @Autowired JdbcTemplate jdbc;
    @Autowired InterventionService workflow;
    @Autowired GoalCatalogService catalog;
    @Autowired GoalEvaluationService progress;

    @Test @Transactional(readOnly=true)
    void importedProgramsExposeEachDomainOnceAndLoadGoalsForTheExactLevelDomain() {
        var programs=workflow.programs();
        assertTrue(programs.stream().anyMatch(p->"ESDM".equals(p.code())));
        assertTrue(programs.stream().anyMatch(p->"VB".equals(p.code())));
        for(var program:programs) {
            assertEquals(jdbc.queryForObject("SELECT COUNT(*) FROM domains WHERE program_id=? AND is_active=1",Long.class,program.id()),program.domainCount());
            assertEquals(jdbc.queryForObject("SELECT COUNT(DISTINCT g.id) FROM goals g JOIN program_level_domains ld ON ld.id=g.level_domain_id JOIN domains d ON d.id=ld.domain_id WHERE ld.program_id=? AND g.is_active=1 AND d.is_active=1",Long.class,program.id()),program.goalCount());
            var levels=catalog.levels(program.id());
            assertEquals(levels.stream().mapToInt(l->l.ageMinMonths()).min().orElseThrow(),program.ageMinMonths());
            assertEquals(levels.stream().mapToInt(l->l.ageMaxMonths()).max().orElseThrow(),program.ageMaxMonths());
            var fields=workflow.domains(program.id());
            assertEquals(program.domainCount(),fields.size());
            assertEquals(fields.size(),fields.stream().map(DomainGroup::id).distinct().count());
            var uniqueGoals=new HashSet<String>();
            for(var field:fields) {
                assertEquals(field.ages().size(),field.ages().stream().map(AgeOption::levelId).distinct().count());
                for(var age:field.ages()) {
                    assertEquals(field.id(),age.domainId());
                    var targets=workflow.targets(age.levelDomainId());
                    var expected=jdbc.queryForList("SELECT id FROM goals WHERE level_domain_id=? AND is_active=1 ORDER BY display_order,id",String.class,age.levelDomainId());
                    assertEquals(expected,targets.goals().stream().map(GoalChoice::id).toList());
                    for(var goal:targets.goals()) assertTrue(uniqueGoals.add(goal.id()));
                    if("ESDM".equals(program.code())) {
                        assertFalse(targets.supportsSpecificGoals());
                        assertTrue(targets.goals().stream().allMatch(g->!g.hasSpecificGoals()&&g.canSelectGeneralGoal()));
                    }
                }
            }
            assertEquals(program.goalCount(),uniqueGoals.size());
        }
    }

    @Test @Transactional
    void fivePageFlowUsesImportedDenverAndVbAndSavesTheReviewedOutline() {
        String student=UUID.randomUUID().toString();
        String actor=jdbc.queryForObject("SELECT id FROM app_user WHERE role='ADMIN' AND status='ACTIVE' ORDER BY id LIMIT 1",String.class);
        jdbc.update("INSERT INTO student(id,code,full_name,birth_date,admitted_on) VALUES(?,?,?,'2022-01-01','2023-01-01')",
            student,"FLOW-"+student.substring(0,8),"Temporary imported catalog verification");
        assertEquals(0,workflow.students("").stream().filter(s->student.equals(s.id())).findFirst().orElseThrow().studyingGoals());
        assertTrue(workflow.workspace(student).plans().isEmpty());
        int assigned=0;
        for(String programCode:List.of("ESDM","VB")) {
            var program=workflow.programs().stream().filter(p->programCode.equals(p.code())).findFirst().orElseThrow();
            var fields=workflow.domains(program.id());
            var targets=new ArrayList<Target>();
            // Deliberately click fields and child checkboxes in reverse library order.
            for(var field:fields.reversed().subList(0,2)) {
                var age=field.ages().getFirst();
                var main=workflow.targets(age.levelDomainId()).goals();
                if(main.isEmpty()) continue;
                var detail=catalog.goal(main.getFirst().id());
                if(detail.supportsSpecificGoals()&&!detail.specificGoals().isEmpty()) {
                    for(var child:detail.specificGoals().stream().limit(2).toList().reversed()) targets.add(new Target(detail.id(),child.id()));
                } else targets.add(new Target(detail.id(),null));
            }
            assertFalse(targets.isEmpty());
            var draft=new Draft(UUID.randomUUID().toString(),program.id(),"Imported "+programCode,LocalDate.now(),targets);
            var checked=workflow.preview(student,draft);
            assertEquals(assigned,workflow.workspace(student).student().studyingGoals());
            var expectedOrder=jdbc.queryForList("SELECT g.id FROM goals g JOIN program_level_domains ld ON ld.id=g.level_domain_id JOIN domains d ON d.id=ld.domain_id JOIN program_levels pl ON pl.id=ld.program_level_id WHERE ld.program_id=? ORDER BY d.display_order,d.id,pl.display_order,pl.id,g.display_order,g.id",String.class,program.id());
            int previous=-1;
            for(var item:checked.items()) {
                int order=expectedOrder.indexOf(item.goalId()); assertTrue(order>=previous); previous=order;
            }
            var request=new Complete(checked.requestId(),checked.programId(),checked.name(),checked.startDate(),
                checked.items().stream().map(s->new Target(s.goalId(),s.specificGoalId())).toList(),checked.reviewToken());
            var saved=workflow.complete(student,request,actor);
            assertFalse(saved.alreadySaved()); assertTrue(workflow.complete(student,request,actor).alreadySaved());
            assertEquals(checked.items().size(),saved.result().goals().size());
            for(var row:saved.result().goals()) {
                assertEquals(program.id(),row.goalSnapshot().path("program_id").asText());
                assertTrue(row.goalSnapshot().has("level_domain_id"));
                if("ESDM".equals(programCode)) assertNull(row.specificGoalId());
            }
            assigned+=checked.total();
            var reread=workflow.workspace(student);
            assertEquals(assigned,reread.student().studyingGoals());
            String planGoal=saved.result().goals().getFirst().id();
            progress.create(saved.result().plan().id(),planGoal,new Input(new java.math.BigDecimal("80"),"RETRY",null),new AppUserPrincipal(actor,"admin@test.invalid","Test","ADMIN","unused"));
            assertEquals(1,workflow.workspace(student).student().achievedGoals());
            progress.create(saved.result().plan().id(),planGoal,new Input(new java.math.BigDecimal("50"),null,null),new AppUserPrincipal(actor,"admin@test.invalid","Test","ADMIN","unused"));
        }
        assertEquals(2,workflow.workspace(student).plans().size());
        // Spring rolls back students, learning_plans and plan_goals after the test.
    }
}

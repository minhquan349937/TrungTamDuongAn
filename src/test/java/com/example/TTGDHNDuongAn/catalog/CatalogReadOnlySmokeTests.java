package com.example.TTGDHNDuongAn.catalog;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.example.TTGDHNDuongAn.repository.GoalCatalogRepository;
import com.example.TTGDHNDuongAn.repository.PlanGoalRepository;
import com.example.TTGDHNDuongAn.service.InterventionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/** Opt-in, SELECT-only verification of a migrated database. Creates no fixture or account. */
@SpringBootTest(properties = {
        "spring.datasource.url=${GOAL_CATALOG_SMOKE_DB_URL}",
        "SETUP_ADMIN_EMAIL=", "SETUP_ADMIN_PASSWORD=",
        "spring.docker.compose.enabled=false", "debug=false", "logging.level.root=WARN"
})
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "GOAL_CATALOG_SMOKE_DB_URL", matches = ".+")
class CatalogReadOnlySmokeTests {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired GoalCatalogRepository catalog;
    @Autowired PlanGoalRepository plans;
    @Autowired InterventionService workflow;

    @Test
    @Transactional(readOnly = true)
    void readsMigratedCatalogAndAssignmentColumnsThroughTheRealBackend() throws Exception {
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() " +
            "AND table_name IN ('student_goal_bank','goal_criteria','progress_records')",Integer.class));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM plan_goals pg WHERE pg.status<>COALESCE(" +
            "(SELECT e.result_status FROM goal_evaluations e WHERE e.plan_goal_id=pg.id ORDER BY attempt_no DESC LIMIT 1),'NOT_STARTED')",Integer.class));
        assertEquals(5, jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.columns " +
                "WHERE table_schema=DATABASE() AND table_name IN ('programs','program_levels','domains','goals','specific_goals') " +
                "AND column_name='id' AND data_type='varchar' AND character_maximum_length=8", Integer.class));
        assertNotNull(catalog.programs());
        assertNotNull(catalog.levels("PR000000"));
        assertNotNull(catalog.domains("LV000000"));
        assertNotNull(catalog.goals("DM000000", "", 0, 20));
        assertTrue(catalog.goalHeader("GL000000").isEmpty());
        assertTrue(plans.list("00000000-0000-0000-0000-000000000000").isEmpty());
        var school=workflow.students("");
        for(var student:school.stream().limit(3).toList()) assertEquals(student.id(),workflow.workspace(student.id()).student().id());
        for(var program:workflow.programs()) assertNotNull(workflow.domains(program.id()));
        mvc.perform(get("/api/intervention/programs").with(user("smoke-reader").roles("ADMIN")))
                .andExpect(status().isOk()).andExpect(jsonPath("$").isArray());
        mvc.perform(get("/api/programs").with(user("smoke-reader").roles("ADMIN")))
                .andExpect(status().isOk()).andExpect(jsonPath("$").isArray());
        mvc.perform(get("/api/goals/GL000000").with(user("smoke-reader").roles("TEACHER")))
                .andExpect(status().isNotFound());
        var denver=catalog.programs().stream().filter(row -> "ESDM".equals(row.code())).findFirst();
        if(denver.isPresent()) {
            var goalIds=jdbc.queryForList("SELECT g.id FROM goals g JOIN program_level_domains ld ON ld.id=g.level_domain_id JOIN domains d ON d.id=ld.domain_id " +
                    "JOIN program_levels pl ON pl.id=ld.program_level_id WHERE pl.program_id=? AND g.is_active=1 AND d.is_active=1 ORDER BY g.id LIMIT 1",
                    String.class,denver.get().id());
            if(!goalIds.isEmpty()) mvc.perform(get("/api/goals/"+goalIds.getFirst()).with(user("smoke-reader").roles("ADMIN")))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.supportsSpecificGoals").value(false))
                    .andExpect(jsonPath("$.canSelectGeneralGoal").value(true)).andExpect(jsonPath("$.specificGoals.length()").value(0));
        }
    }
}

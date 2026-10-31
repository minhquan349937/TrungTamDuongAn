package com.example.TTGDHNDuongAn.catalog;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataAccessException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import com.example.TTGDHNDuongAn.security.AppUserPrincipal;

@SpringBootTest(properties = "spring.datasource.url=${GOAL_CATALOG_TEST_DB_URL}")
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "GOAL_CATALOG_TEST_DB_URL", matches = ".+")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GoalCatalogApiTests {
    private static final String DENVER = "PR0101";
    private static final String VB = "PR0102";
    private static final String DENVER_LEVEL = "LV0103";
    private static final String VB_LEVEL = "LV0104";
    private static final String DENVER_DOMAIN = "DM0105";
    private static final String VB_DOMAIN = "DM0106";
    private static final String DENVER_GOAL = "GL000107";
    private static final String VB_GOAL = "GL000108";
    private static final String VB_NO_CHILD = "GL000109";
    private static final String VB_CHILD_A = "SG000110";
    private static final String VB_CHILD_B = "SG000111";
    private static final String ACTOR = "00000000-0000-0000-0000-000000000112";
    private static final String STUDENT = "00000000-0000-0000-0000-000000000113";
    private static final String PLAN = "00000000-0000-0000-0000-000000000114";

    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;

    @BeforeAll
    void createFixtures() {
        jdbc.update("INSERT INTO app_user(id,email,password_hash,display_name,role) VALUES(?,?,?,?,?)",
                ACTOR, "goal-test@example.invalid", "unused", "Goal test actor", "ADMIN");
        jdbc.update("INSERT INTO student(id,code,full_name,birth_date,admitted_on) VALUES(?,?,?,'2022-01-01','2023-01-01')",
                STUDENT, "GOAL-TEST", "Test student");
        jdbc.update("INSERT INTO learning_plans(id,student_id,name,start_date,created_by) VALUES(?,?,?,'2026-01-01',?)",
                PLAN, STUDENT, "Test plan", ACTOR);
        jdbc.update("INSERT INTO programs(id,code,name) VALUES(?,?,?),(?,?,?)",
                DENVER, "DENVER-TEST", "Denver", VB, "VB-TEST", "VB");
        jdbc.update("INSERT INTO program_levels(id,program_id,level_number,name,age_min_months,age_max_months,display_order) " +
                        "VALUES(?,?,1,'Level 1',12,24,1),(?,?,1,'Level 1',0,48,1)",
                DENVER_LEVEL, DENVER, VB_LEVEL, VB);
        jdbc.update("INSERT INTO domains(id,program_id,code,name,display_order) " +
                        "VALUES(?,?,'GTN','Language',1),(?,?,'GTN','Communication',1)",
                DENVER_DOMAIN, DENVER, VB_DOMAIN, VB);
        jdbc.update("INSERT INTO program_level_domains(id,program_id,program_level_id,domain_id) VALUES('LD0105',?,?,?),('LD0106',?,?,?)",DENVER,DENVER_LEVEL,DENVER_DOMAIN,VB,VB_LEVEL,VB_DOMAIN);
        jdbc.update("INSERT INTO goals(id,level_domain_id,code,name,description,display_order) " +
                        "VALUES(?,?,?,?,?,1),(?,?,?,?,?,1),(?,?,?,?,?,2)",
                DENVER_GOAL, "LD0105", "GTN.1.1", "Locate a sound", "Turn head toward the source",
                VB_GOAL, "LD0106", "VB.1.1", "Make two requests", null,
                VB_NO_CHILD, "LD0106", "VB.1.2", "Respond to a request", null);
        jdbc.update("INSERT INTO specific_goals(id,goal_id,code,description,age_min_months,age_max_months,display_order) " +
                        "VALUES(?,?,?,?,NULL,NULL,1),(?,?,?,?,24,36,2)",
                VB_CHILD_A, VB_GOAL, "VB.1.1A", "Eye contact",
                VB_CHILD_B, VB_GOAL, "VB.1.1B", "Approach object");
    }

    @AfterAll
    void removeFixtures() {
        jdbc.update("DELETE FROM goal_evaluations WHERE plan_goal_id IN (SELECT id FROM plan_goals WHERE plan_id=?)", PLAN);
        jdbc.update("DELETE FROM plan_goals WHERE plan_id=?", PLAN);
        jdbc.update("DELETE FROM learning_plans WHERE id=?", PLAN);
        jdbc.update("DELETE FROM specific_goals WHERE goal_id IN (?,?)", VB_GOAL, VB_NO_CHILD);
        jdbc.update("DELETE FROM goals WHERE level_domain_id IN (?,?)", "LD0105", "LD0106");
        jdbc.update("DELETE FROM program_level_domains WHERE id IN ('LD0105','LD0106')");
        jdbc.update("DELETE FROM domains WHERE id IN (?,?)", DENVER_DOMAIN, VB_DOMAIN);
        jdbc.update("DELETE FROM program_levels WHERE id IN (?,?)", DENVER_LEVEL, VB_LEVEL);
        jdbc.update("DELETE FROM programs WHERE id IN (?,?)", DENVER, VB);
        jdbc.update("DELETE FROM student WHERE id=?", STUDENT);
        jdbc.update("DELETE FROM app_user WHERE id=?", ACTOR);
    }

    @Test
    void listsProgramsLevelsAndDomains() throws Exception {
        mvc.perform(get("/api/programs").with(user("reader").roles("TEACHER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.code == 'DENVER-TEST')].name").value("Denver"));
        mvc.perform(get("/api/programs/{id}/levels", DENVER).with(user("reader").roles("TEACHER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].ageMinMonths").value(12));
        mvc.perform(get("/api/levels/{id}/domains", DENVER_LEVEL).with(user("reader").roles("TEACHER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].code").value("GTN"));
    }

    @Test
    void returnsDenverAndBothVbGoalShapes() throws Exception {
        mvc.perform(get("/api/goals/{id}", DENVER_GOAL).with(user("reader").roles("TEACHER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.description").value("Turn head toward the source"))
                .andExpect(jsonPath("$.specificGoals.length()").value(0));
        mvc.perform(get("/api/goals/{id}", VB_GOAL).with(user("reader").roles("TEACHER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.description").isEmpty())
                .andExpect(jsonPath("$.specificGoals.length()").value(2))
                .andExpect(jsonPath("$.specificGoals[0].ageMinMonths").value(0))
                .andExpect(jsonPath("$.specificGoals[1].ageMinMonths").value(24));
        mvc.perform(get("/api/goals/{id}", VB_NO_CHILD).with(user("reader").roles("TEACHER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.specificGoals.length()").value(0));
    }

    @Test
    void searchesAndPagesGoals() throws Exception {
        mvc.perform(get("/api/level-domains/{id}/goals", "LD0106").param("q", "VB.1")
                        .param("page", "1").param("size", "1").with(user("reader").roles("TEACHER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].code").value("VB.1.2"));
    }

    @Test
    void rejectsInvalidAndUnknownIds() throws Exception {
        mvc.perform(get("/api/goals/not-a-uuid").with(user("reader").roles("TEACHER")))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/goals/GL999999")
                        .with(user("reader").roles("TEACHER"))).andExpect(status().isNotFound());
        mvc.perform(get("/api/level-domains/{id}/goals", "LD0106").param("size", "0")
                        .with(user("reader").roles("TEACHER"))).andExpect(status().isBadRequest());
    }

    @Test
    void duplicateGoalCodeReturnsConflictAndWriteRequiresAdmin() throws Exception {
        String body = """
                {"code":"VB.1.1","name":"Duplicate","displayOrder":5}
                """;
        mvc.perform(post("/api/level-domains/{id}/goals", "LD0106").with(user("teacher").roles("TEACHER"))
                        .with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/level-domains/{id}/goals", "LD0106").with(user("admin").roles("ADMIN"))
                        .with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict());
    }

    @Test
    void hierarchyAndAgeConstraintsRejectInvalidRows() {
        org.junit.jupiter.api.Assertions.assertThrows(DataAccessException.class,
                () -> jdbc.update("INSERT INTO program_levels(id,program_id,level_number,name,age_min_months,age_max_months,display_order) " +
                                "VALUES(?, ?, 2, 'Invalid', 30, 12, 2)",
                        "LV0119", DENVER));
        org.junit.jupiter.api.Assertions.assertThrows(DataAccessException.class,
                () -> jdbc.update("INSERT INTO domains(id,program_id,code,name,display_order) VALUES(?,?,?,?,1)",
                        "DM0120", DENVER, "GTN", "Duplicate"));
        org.junit.jupiter.api.Assertions.assertThrows(DataAccessException.class,
                () -> jdbc.update("INSERT INTO specific_goals(id,goal_id,code,description,age_min_months,age_max_months) " +
                                "VALUES(?,?,?,?,30,12)",
                        "SG000121", VB_GOAL, "VB.BAD", "Invalid"));
        org.junit.jupiter.api.Assertions.assertThrows(DataAccessException.class,
                () -> jdbc.update("DELETE FROM programs WHERE id=?", DENVER));
    }

    @Test
    void planCanAssignGeneralOrSpecificGoalWithoutChangingPastSnapshots() {
        String direct = "00000000-0000-0000-0000-000000000115";
        String specific = "00000000-0000-0000-0000-000000000116";
        jdbc.update("INSERT INTO plan_goals(id,plan_id,goal_id,goal_snapshot,criteria_snapshot,assigned_by) " +
                        "VALUES(?,?,?,JSON_OBJECT(),JSON_OBJECT(),?)", direct, PLAN, DENVER_GOAL, ACTOR);
        jdbc.update("INSERT INTO plan_goals(id,plan_id,goal_id,specific_goal_id,goal_snapshot,criteria_snapshot,assigned_by) " +
                        "VALUES(?,?,?,?,JSON_OBJECT(),JSON_OBJECT(),?)", specific, PLAN, VB_GOAL, VB_CHILD_A, ACTOR);
        try {
            String snapshot = jdbc.queryForObject("SELECT JSON_UNQUOTE(JSON_EXTRACT(goal_snapshot,'$.name')) " +
                    "FROM plan_goals WHERE id=?", String.class, direct);
            jdbc.update("UPDATE goals SET name='Edited later' WHERE id=?", DENVER_GOAL);
            try {
                org.junit.jupiter.api.Assertions.assertEquals(snapshot,
                        jdbc.queryForObject("SELECT JSON_UNQUOTE(JSON_EXTRACT(goal_snapshot,'$.name')) " +
                                "FROM plan_goals WHERE id=?", String.class, direct));
            } finally {
                jdbc.update("UPDATE goals SET name=? WHERE id=?", "Locate a sound", DENVER_GOAL);
            }
            org.junit.jupiter.api.Assertions.assertThrows(DataAccessException.class,
                    () -> jdbc.update("INSERT INTO plan_goals(id,plan_id,goal_id,goal_snapshot,criteria_snapshot,assigned_by) " +
                                    "VALUES(?,?,?,JSON_OBJECT(),JSON_OBJECT(),?)",
                            "00000000-0000-0000-0000-000000000117", PLAN, DENVER_GOAL, ACTOR));
            jdbc.update("INSERT INTO goal_evaluations(id,plan_goal_id,attempt_no,evaluated_at,evaluated_by) " +
                            "VALUES(?,?,1,UTC_TIMESTAMP(6),?)",
                    "00000000-0000-0000-0000-000000000118", specific, ACTOR);
        } finally {
            jdbc.update("DELETE FROM goal_evaluations WHERE plan_goal_id=?", specific);
            jdbc.update("DELETE FROM plan_goals WHERE id IN (?,?)", direct, specific);
        }
    }

    @Test
    void planApiAssignsEitherTargetAndReturnsSnapshots() throws Exception {
        AppUserPrincipal admin = new AppUserPrincipal(ACTOR, "goal-test@example.invalid", "Goal test actor",
                "ADMIN", "unused");
        try {
            mvc.perform(post("/api/plans/{id}/goals", PLAN).with(user(admin)).with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"goalId\":\"" + DENVER_GOAL + "\"}"))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.goalSnapshot.target_type").value("GOAL"));
            mvc.perform(post("/api/plans/{id}/goals", PLAN).with(user(admin)).with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"goalId\":\"" + VB_GOAL + "\",\"specificGoalId\":\"" + VB_CHILD_A + "\"}"))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.goalSnapshot.target_type").value("SPECIFIC_GOAL"));
            mvc.perform(get("/api/plans/{id}/goals", PLAN).with(user(admin)))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2));
            mvc.perform(post("/api/plans/{id}/goals", PLAN).with(user(admin)).with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"goalId\":\"" + DENVER_GOAL + "\"}"))
                    .andExpect(status().isConflict());
            mvc.perform(post("/api/plans/{id}/goals", PLAN).with(user(admin)).with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"goalId\":\"bad\"}"))
                    .andExpect(status().isBadRequest());
        } finally {
            jdbc.update("DELETE FROM plan_goals WHERE plan_id=?", PLAN);
        }
    }

    @Test
    void studentAndPlanApiUseRealRows() throws Exception {
        AppUserPrincipal admin = new AppUserPrincipal(ACTOR, "goal-test@example.invalid", "Goal test actor",
                "ADMIN", "unused");
        mvc.perform(get("/api/students").with(user(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.code == 'GOAL-TEST')].fullName").value("Test student"));
        mvc.perform(get("/api/students/{id}/plans", STUDENT).with(user(admin)))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].name").value("Test plan"));
        mvc.perform(get("/api/students").with(user("teacher").roles("TEACHER")))
                .andExpect(status().isForbidden());
        try {
            mvc.perform(post("/api/students", STUDENT).with(user(admin)).with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"code":"GOAL-NEW","fullName":"New student","birthDate":"2022-01-01","admittedOn":"2023-01-01"}
                                    """))
                    .andExpect(status().isCreated());
            mvc.perform(post("/api/students/{id}/plans", STUDENT).with(user(admin)).with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{" + "\"name\":\"Created via API\",\"startDate\":\"2026-01-01\"}"))
                    .andExpect(status().isCreated());
            mvc.perform(get("/api/students/{id}/plans", STUDENT).with(user(admin)))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2));
        } finally {
            jdbc.update("DELETE FROM learning_plans WHERE student_id=? AND name=?", STUDENT, "Created via API");
            jdbc.update("DELETE FROM student WHERE code=?", "GOAL-NEW");
        }
    }
}

package com.example.TTGDHNDuongAn.repository;

import java.util.List;
import java.util.UUID;

import com.example.TTGDHNDuongAn.dto.PlanGoalDtos;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.ObjectMapper;

@Repository
public class PlanGoalRepository {
    private static final String GOAL_COLUMNS = "SELECT pg.*,(SELECT e.achievement_percent FROM goal_evaluations e " +
        "WHERE e.plan_goal_id=pg.id ORDER BY e.attempt_no DESC LIMIT 1) achievement_percent ";
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper = new ObjectMapper();

    public PlanGoalRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public boolean planExists(String planId) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM learning_plans WHERE id=?", Integer.class, planId);
        return count != null && count > 0;
    }
    public String planStudent(String planId) {
        return jdbc.query("SELECT student_id FROM learning_plans WHERE id=?",rs->rs.next()?rs.getString(1):null,planId);
    }
    public String lockPlanStudent(String planId) {
        return jdbc.query("SELECT student_id FROM learning_plans WHERE id=? FOR UPDATE",rs->rs.next()?rs.getString(1):null,planId);
    }
    public String lockCurrentTarget(String planId,String rowId) {
        return jdbc.query("SELECT CONCAT(pg.goal_id,':',COALESCE(pg.specific_goal_id,'')) FROM plan_goals pg " +
            "WHERE pg.plan_id=? AND pg.id=? AND NOT EXISTS " +
            "(SELECT 1 FROM plan_goals successor WHERE successor.supersedes_id=pg.id) FOR UPDATE",
            rs->rs.next()?rs.getString(1):null,planId,rowId);
    }
    public boolean currentTargetExists(String planId,String goalId,String specificGoalId) {
        Integer count=jdbc.queryForObject("SELECT COUNT(*) FROM plan_goals pg WHERE pg.plan_id=? AND pg.goal_id=? " +
            "AND (pg.specific_goal_id <=> ?) AND NOT EXISTS (SELECT 1 FROM plan_goals successor WHERE successor.supersedes_id=pg.id)",
            Integer.class,planId,goalId,specificGoalId);
        return count!=null && count>0;
    }

    public boolean activeTargetExists(String goalId, String specificGoalId) {
        String sql = specificGoalId == null
                ? "SELECT COUNT(*) FROM goals g JOIN program_level_domains ld ON ld.id=g.level_domain_id JOIN domains d ON d.id=ld.domain_id " +
                  "JOIN program_levels pl ON pl.id=ld.program_level_id JOIN programs p ON p.id=pl.program_id " +
                  "WHERE g.id=? AND g.is_active=1 AND d.is_active=1 AND p.is_active=1"
                : "SELECT COUNT(*) FROM specific_goals sg JOIN goals g ON g.id=sg.goal_id " +
                  "JOIN program_level_domains ld ON ld.id=g.level_domain_id JOIN domains d ON d.id=ld.domain_id JOIN program_levels pl ON pl.id=ld.program_level_id " +
                  "JOIN programs p ON p.id=pl.program_id " +
                  "WHERE g.id=? AND sg.id=? AND sg.is_active=1 AND g.is_active=1 AND d.is_active=1 AND p.is_active=1";
        Integer count = specificGoalId == null ? jdbc.queryForObject(sql, Integer.class, goalId)
                : jdbc.queryForObject(sql, Integer.class, goalId, specificGoalId);
        return count != null && count > 0;
    }

    public String insert(String planId, String goalId, String specificGoalId, String actorId) {
        String id = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO plan_goals(id,plan_id,goal_id,specific_goal_id,goal_snapshot,criteria_snapshot,assigned_by) " +
                        "VALUES(?,?,?,?,JSON_OBJECT(),JSON_OBJECT(),?)", id, planId, goalId, specificGoalId, actorId);
        return id;
    }
    public String insertReplacement(String planId,String oldRowId,String goalId,String specificGoalId,String actorId) {
        String id=UUID.randomUUID().toString();
        jdbc.update("INSERT INTO plan_goals(id,plan_id,goal_id,specific_goal_id,goal_snapshot,criteria_snapshot,assigned_by,supersedes_id) " +
            "VALUES(?,?,?,?,JSON_OBJECT(),JSON_OBJECT(),?,?)",id,planId,goalId,specificGoalId,actorId,oldRowId);
        return id;
    }

    public List<PlanGoalDtos.AssignedGoal> list(String planId) {
        return read(GOAL_COLUMNS+"FROM plan_goals pg WHERE pg.plan_id=? AND NOT EXISTS " +
            "(SELECT 1 FROM plan_goals successor WHERE successor.supersedes_id=pg.id) ORDER BY pg.assigned_at,pg.id",planId);
    }

    public List<PlanGoalDtos.AssignedGoal> listForStudent(String studentId) {
        return read(GOAL_COLUMNS+"FROM plan_goals pg JOIN learning_plans lp ON lp.id=pg.plan_id WHERE lp.student_id=? " +
                "AND NOT EXISTS (SELECT 1 FROM plan_goals successor WHERE successor.supersedes_id=pg.id) " +
                "ORDER BY pg.assigned_at,pg.id",studentId);
    }

    private List<PlanGoalDtos.AssignedGoal> read(String sql,String id) {
        return jdbc.query(sql,(rs, n) -> new PlanGoalDtos.AssignedGoal(
                        rs.getString("id"), rs.getString("plan_id"),
                        rs.getString("goal_id"), rs.getString("specific_goal_id"),
                        rs.getString("specific_goal_id") == null ? "GOAL" : "SPECIFIC_GOAL",
                        rs.getString("specific_goal_id") == null ? rs.getString("goal_id") : rs.getString("specific_goal_id"),
                        mapper.readTree(rs.getString("goal_snapshot")),
                        mapper.readTree(rs.getString("criteria_snapshot")),
                        rs.getString("status"),rs.getBigDecimal("achievement_percent")), id);
    }
}

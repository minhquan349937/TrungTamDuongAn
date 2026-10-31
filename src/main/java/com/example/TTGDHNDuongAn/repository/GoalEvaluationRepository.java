package com.example.TTGDHNDuongAn.repository;

import com.example.TTGDHNDuongAn.dto.GoalEvaluationDtos.*;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class GoalEvaluationRepository {
    private final JdbcTemplate jdbc;
    public GoalEvaluationRepository(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    public String lockPlanStudent(String plan) {
        return jdbc.queryForList("SELECT student_id FROM learning_plans WHERE id=? FOR UPDATE",String.class,plan)
            .stream().findFirst().orElse(null);
    }
    public boolean lockGoal(String plan,String goal) {
        return !jdbc.queryForList("SELECT id FROM plan_goals WHERE plan_id=? AND id=? FOR UPDATE",String.class,plan,goal).isEmpty();
    }
    public int nextAttempt(String goal) {
        return jdbc.queryForObject("SELECT COALESCE(MAX(attempt_no),0)+1 FROM goal_evaluations WHERE plan_goal_id=?",Integer.class,goal);
    }
    public void insert(String id,String goal,int attempt,Input input,String actor) {
        jdbc.update("INSERT INTO goal_evaluations(id,plan_goal_id,attempt_no,achievement_percent,restart_decision,evaluation_notes,evaluated_at,evaluated_by) " +
            "VALUES(?,?,?,?,?,?,IF(? IS NULL,NULL,UTC_TIMESTAMP(6)),?)",id,goal,attempt,input.achievementPercent(),
            input.restartDecision(),input.evaluationNotes(),input.achievementPercent(),actor);
    }
    public void update(String id,Input input) {
        jdbc.update("UPDATE goal_evaluations SET achievement_percent=?,restart_decision=?,evaluation_notes=?," +
            "evaluated_at=IF(? IS NULL,NULL,UTC_TIMESTAMP(6)) WHERE id=?",input.achievementPercent(),input.restartDecision(),
            input.evaluationNotes(),input.achievementPercent(),id);
    }
    public List<Evaluation> list(String goal) {
        return jdbc.query("SELECT e.*,u.display_name FROM goal_evaluations e JOIN app_user u ON u.id=e.evaluated_by " +
            "WHERE e.plan_goal_id=? ORDER BY e.attempt_no DESC",(rs,n)->new Evaluation(rs.getString("id"),
                rs.getString("plan_goal_id"),rs.getInt("attempt_no"),rs.getBigDecimal("achievement_percent"),
                rs.getString("result_status"),rs.getString("restart_decision"),rs.getString("evaluation_notes"),
                rs.getTimestamp("evaluated_at")==null?null:rs.getTimestamp("evaluated_at").toLocalDateTime(),
                rs.getString("evaluated_by"),rs.getString("display_name"),rs.getTimestamp("created_at").toLocalDateTime(),
                rs.getTimestamp("updated_at").toLocalDateTime()),goal);
    }
}

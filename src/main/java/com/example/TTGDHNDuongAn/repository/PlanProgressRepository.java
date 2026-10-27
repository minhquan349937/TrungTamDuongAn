package com.example.TTGDHNDuongAn.repository;

import com.example.TTGDHNDuongAn.dto.PlanProgressDtos.*;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class PlanProgressRepository {
    private final JdbcTemplate jdbc;
    public PlanProgressRepository(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    public boolean lockPlan(String id) {
        return !jdbc.queryForList("SELECT id FROM learning_plans WHERE id=? FOR UPDATE",String.class,id).isEmpty();
    }
    public boolean lockGoal(String plan,String goal) {
        return !jdbc.queryForList("SELECT id FROM plan_goals WHERE plan_id=? AND id=? FOR UPDATE",String.class,plan,goal).isEmpty();
    }
    public void edit(String id,EditPlan request) {
        jdbc.update("UPDATE learning_plans SET name=?,start_date=?,status=? WHERE id=?",request.name().trim(),request.startDate(),request.status(),id);
    }
    public void status(String id,String status) { jdbc.update("UPDATE plan_goals SET status=? WHERE id=?",status,id); }
    public String observe(String id,Observation observation,String actor) {
        String key=UUID.randomUUID().toString();
        jdbc.update("INSERT INTO progress_records(id,plan_goal_id,observed_at,notes,success_count,opportunity_count,recorded_by) VALUES(?,?,UTC_TIMESTAMP(6),?,?,?,?)",
            key,id,observation.notes().trim(),observation.successCount(),observation.opportunityCount(),actor);
        return key;
    }
    public List<Progress> list(String plan,String goal) {
        return jdbc.query("SELECT pr.* FROM progress_records pr JOIN plan_goals pg ON pg.id=pr.plan_goal_id WHERE pg.plan_id=? AND pg.id=? ORDER BY pr.observed_at DESC,pr.id",
            (rs,n)->new Progress(rs.getString("id"),rs.getString("plan_goal_id"),rs.getTimestamp("observed_at").toLocalDateTime(),
                rs.getString("notes"),rs.getObject("success_count",Integer.class),rs.getObject("opportunity_count",Integer.class),rs.getString("recorded_by")),plan,goal);
    }
}

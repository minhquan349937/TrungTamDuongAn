package com.example.TTGDHNDuongAn.service;

import com.example.TTGDHNDuongAn.dto.InterventionDtos.*;
import com.example.TTGDHNDuongAn.dto.GoalSelectionDtos.Target;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class StudentGoalHistoryService {
    private final JdbcTemplate jdbc;
    public StudentGoalHistoryService(JdbcTemplate jdbc) { this.jdbc=jdbc; }

    // A newly planned/unperformed attempt does not erase an earlier measured result.
    // Only non-cancelled plans contribute; main and specific targets have separate identities.
    public List<GoalLearningHistory> list(String student) {
        return jdbc.query("WITH assigned AS (" +
            "SELECT pg.id,pg.goal_id,pg.specific_goal_id,pg.assigned_at," +
            "IF(pg.specific_goal_id IS NULL,CONCAT('G:',pg.goal_id),CONCAT('S:',pg.specific_goal_id)) target_key " +
            "FROM plan_goals pg JOIN learning_plans lp ON lp.id=pg.plan_id WHERE lp.student_id=? AND lp.status<>'CANCELLED'), " +
            "known AS (SELECT DISTINCT goal_id,specific_goal_id,target_key FROM assigned), " +
            "measured AS (SELECT a.target_key,e.achievement_percent,e.result_status,e.evaluated_at," +
            "ROW_NUMBER() OVER(PARTITION BY a.target_key ORDER BY e.evaluated_at DESC,e.created_at DESC,a.assigned_at DESC,e.attempt_no DESC,e.id DESC) rn " +
            "FROM assigned a JOIN goal_evaluations e ON e.plan_goal_id=a.id WHERE e.achievement_percent IS NOT NULL " +
            "AND NOT EXISTS(SELECT 1 FROM goal_evaluations newer WHERE newer.plan_goal_id=e.plan_goal_id " +
            "AND newer.attempt_no>e.attempt_no AND newer.achievement_percent IS NOT NULL)) " +
            "SELECT k.goal_id,k.specific_goal_id,COALESCE(m.result_status,'NOT_STARTED') result_status,m.achievement_percent,m.evaluated_at " +
            "FROM known k LEFT JOIN measured m ON m.target_key=k.target_key AND m.rn=1 ORDER BY k.target_key",
            (rs,n)->new GoalLearningHistory(rs.getString("goal_id"),rs.getString("specific_goal_id"),rs.getString("result_status"),
                rs.getBigDecimal("achievement_percent"),rs.getTimestamp("evaluated_at")==null?null:rs.getTimestamp("evaluated_at").toLocalDateTime()),student);
    }

    public StudentDomainTargets targets(String student,DomainTargets catalog) {
        List<GoalLearningHistory> all=list(student);
        Set<String> achieved=new HashSet<>();
        all.stream().filter(h->"ACHIEVED".equals(h.resultStatus())).forEach(h->achieved.add(h.key()));
        Set<String> goalsWithRemainingChildren=new HashSet<>();
        if(catalog.supportsSpecificGoals() && !catalog.goals().isEmpty()) {
            var ids=catalog.goals().stream().map(GoalChoice::id).toList();
            String placeholders=String.join(",",Collections.nCopies(ids.size(),"?"));
            jdbc.query("SELECT id,goal_id FROM specific_goals WHERE is_active=1 AND goal_id IN ("+placeholders+")",
                (org.springframework.jdbc.core.RowCallbackHandler)rs->{
                    if(!achieved.contains("S:"+rs.getString("id"))) goalsWithRemainingChildren.add(rs.getString("goal_id"));
                },ids.toArray());
        }
        var choices=catalog.goals().stream().map(g->new GoalChoice(g.id(),g.code(),g.name(),g.description(),g.displayOrder(),
            goalsWithRemainingChildren.contains(g.id()),g.canSelectGeneralGoal()&&!achieved.contains("G:"+g.id())))
            .filter(g->g.canSelectGeneralGoal()||g.hasSpecificGoals()).toList();
        Set<String> domainGoals=new HashSet<>(catalog.goals().stream().map(GoalChoice::id).toList());
        return new StudentDomainTargets(catalog.supportsSpecificGoals(),choices,all.stream().filter(h->domainGoals.contains(h.goalId())).toList());
    }

    public void requireSelectable(String student,List<Target> selections) {
        Set<String> achieved=new HashSet<>();
        list(student).stream().filter(h->"ACHIEVED".equals(h.resultStatus())).forEach(h->achieved.add(h.key()));
        if(selections.stream().anyMatch(t->achieved.contains(t.specificGoalId()==null?"G:"+t.goalId():"S:"+t.specificGoalId())))
            throw new ResponseStatusException(HttpStatus.CONFLICT,"Có mục tiêu đã đạt trong lựa chọn. Vui lòng tải lại mục tiêu trước khi lập kế hoạch mới.");
    }
}

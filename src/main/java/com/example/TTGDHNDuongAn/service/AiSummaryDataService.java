package com.example.TTGDHNDuongAn.service;

import com.example.TTGDHNDuongAn.repository.ShortIds;
import com.example.TTGDHNDuongAn.security.AppUserPrincipal;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AiSummaryDataService {
    public record Goal(String name, String description, BigDecimal achievementPercent, String status, String notes) {}
    // Deliberately excludes plan names, student IDs/names, birth dates and evaluator identities.
    public record Context(String planStatus, List<Goal> goals) {}
    private final JdbcTemplate jdbc;
    private final PlanAccessService access;
    public AiSummaryDataService(JdbcTemplate jdbc, PlanAccessService access) { this.jdbc=jdbc; this.access=access; }

    @Transactional(readOnly=true)
    public Context load(String planId, AppUserPrincipal actor) {
        if (!"ADMIN".equals(actor.role()) && !"TEACHER".equals(actor.role()))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Chỉ giáo viên phụ trách hoặc quản trị viên được dùng AI.");
        if (!ShortIds.isId(planId)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mã kế hoạch không hợp lệ.");
        var plan=jdbc.queryForList("SELECT student_id,status FROM learning_plans WHERE id=?", planId);
        if (plan.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy kế hoạch.");
        access.requireStudent((String)plan.getFirst().get("student_id"), actor);
        var goals=jdbc.query("""
            SELECT g.name, COALESCE(sg.description,g.description) description,
                   e.achievement_percent, COALESCE(e.result_status,'NOT_STARTED') result_status, e.evaluation_notes
            FROM plan_goals pg JOIN goals g ON g.id=pg.goal_id
            LEFT JOIN specific_goals sg ON sg.id=pg.specific_goal_id
            LEFT JOIN goal_evaluations e ON e.plan_goal_id=pg.id AND e.attempt_no=
                (SELECT MAX(latest.attempt_no) FROM goal_evaluations latest WHERE latest.plan_goal_id=pg.id)
            WHERE pg.plan_id=? AND NOT EXISTS
                (SELECT 1 FROM plan_goals successor WHERE successor.supersedes_id=pg.id AND successor.plan_id=pg.plan_id)
            ORDER BY pg.assigned_at,pg.id
            """, (rs,n)->new Goal(rs.getString("name"),rs.getString("description"),rs.getBigDecimal("achievement_percent"),
                rs.getString("result_status"),rs.getString("evaluation_notes")), planId);
        return new Context((String)plan.getFirst().get("status"), goals);
    }
}

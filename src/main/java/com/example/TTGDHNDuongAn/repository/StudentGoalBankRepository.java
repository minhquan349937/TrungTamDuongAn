package com.example.TTGDHNDuongAn.repository;

import java.util.List;
import java.util.Set;
import java.util.HashSet;
import java.util.UUID;
import com.example.TTGDHNDuongAn.dto.GoalBankDtos;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.ObjectMapper;

@Repository
public class StudentGoalBankRepository {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper=new ObjectMapper();
    public StudentGoalBankRepository(JdbcTemplate jdbc) { this.jdbc=jdbc; }

    public boolean studentExists(String id) {
        return Integer.valueOf(1).equals(jdbc.queryForObject("SELECT COUNT(*) FROM student WHERE id=?",Integer.class,id));
    }
    public boolean lockStudent(String id) {
        return !jdbc.queryForList("SELECT id FROM student WHERE id=? FOR UPDATE",String.class,id).isEmpty();
    }
    public Set<String> existingTargets(String studentId) {
        return new HashSet<>(jdbc.queryForList("SELECT target_key FROM student_goal_bank WHERE student_id=?",String.class,studentId));
    }
    public void insert(String studentId, GoalBankDtos.Selection selection, String actorId) {
        jdbc.update("INSERT INTO student_goal_bank(id,student_id,goal_id,specific_goal_id,goal_snapshot,selected_by) VALUES(?,?,?,?,?,?)",
                UUID.randomUUID().toString(),studentId,selection.goalId(),selection.specificGoalId(),mapper.writeValueAsString(selection),actorId);
    }
    public List<GoalBankDtos.BankEntry> list(String studentId) {
        return jdbc.query("SELECT id,student_id,goal_snapshot,selected_by,created_at FROM student_goal_bank WHERE student_id=? ORDER BY created_at,id",
                (rs,n) -> new GoalBankDtos.BankEntry(rs.getString("id"),rs.getString("student_id"),
                        mapper.readValue(rs.getString("goal_snapshot"),GoalBankDtos.Selection.class),rs.getString("selected_by"),
                        rs.getTimestamp("created_at").toInstant().toString()),studentId);
    }
}

package com.example.TTGDHNDuongAn.service;

import com.example.TTGDHNDuongAn.dto.GoalSelectionDtos.ActivityInput;
import com.example.TTGDHNDuongAn.dto.GoalSelectionDtos.ActivitySelection;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class SuggestedActivityService {
    public record Activity(String id, String goalId, String name, String description) { }
    public record GoalOption(String id, String code, String name, String program, String domain) { }
    public record CreateActivity(String name, String description) { }
    public record LinkActivity(String activityId) { }
    private final JdbcTemplate jdbc;

    public SuggestedActivityService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public List<GoalOption> searchGoals(String query) {
        String text = query == null ? "" : query.strip();
        if (text.length() < 2) return List.of();
        if (text.length() > 100) throw bad("Từ khóa tìm kiếm quá dài.");
        String pattern = "%" + text + "%";
        return jdbc.query("SELECT g.id,g.code,g.name,p.name program,d.name domain FROM goals g " +
            "JOIN program_level_domains ld ON ld.id=g.level_domain_id " +
            "JOIN program_levels pl ON pl.id=ld.program_level_id JOIN programs p ON p.id=pl.program_id " +
            "JOIN domains d ON d.id=ld.domain_id WHERE g.is_active=1 AND p.is_active=1 AND d.is_active=1 " +
            "AND (g.code LIKE ? OR g.name LIKE ?) ORDER BY g.code LIMIT 40",
            (rs,n)->new GoalOption(rs.getString("id"),rs.getString("code"),rs.getString("name"),
                rs.getString("program"),rs.getString("domain")),pattern,pattern);
    }

    public List<Activity> forGoal(String goalId) {
        return jdbc.query("SELECT a.id,a.goal_id,a.name,a.description FROM suggested_activities a " +
            "LEFT JOIN suggested_activity_goals link ON link.activity_id=a.id AND link.goal_id=? " +
            "WHERE (link.goal_id IS NOT NULL OR a.goal_id IS NULL) AND a.is_active=1 ORDER BY a.created_at,a.id",
            (rs,n) -> new Activity(rs.getString("id"),rs.getString("goal_id"),rs.getString("name"),rs.getString("description")),goalId);
    }

    public List<Activity> searchActivities(String query) {
        String text=query==null?"":query.strip();
        if(text.length()<2) return List.of();
        if(text.length()>100) throw bad("Từ khóa tìm kiếm quá dài.");
        return jdbc.query("SELECT id,goal_id,name,description FROM suggested_activities WHERE is_active=1 AND name LIKE ? ORDER BY name,id LIMIT 40",
            (rs,n)->new Activity(rs.getString("id"),rs.getString("goal_id"),rs.getString("name"),rs.getString("description")),"%"+text+"%");
    }

    @Transactional
    public Activity createLibrary(CreateActivity request) {
        if (request == null || request.name() == null || request.name().isBlank() || request.name().strip().length() > 255)
            throw bad("Tên hoạt động phải có từ 1 đến 255 ký tự.");
        String name = request.name().strip();
        var existing = jdbc.query("SELECT id,goal_id,name,description FROM suggested_activities WHERE name=? AND is_active=1 ORDER BY id LIMIT 1",
            (rs,n) -> new Activity(rs.getString("id"),rs.getString("goal_id"),rs.getString("name"),rs.getString("description")), name);
        if (!existing.isEmpty()) return existing.getFirst();
        String id = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO suggested_activities(id,goal_id,name,description) VALUES(?,NULL,?,NULL)",id,name);
        return new Activity(id,null,name,null);
    }

    @Transactional
    public Activity link(String goalId,LinkActivity request) {
        if(request==null || request.activityId()==null) throw bad("Chọn hoạt động có sẵn.");
        Boolean goalExists=jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM goals WHERE id=? AND is_active=1)",Boolean.class,goalId);
        if(!Boolean.TRUE.equals(goalExists)) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Không tìm thấy mục tiêu đang sử dụng.");
        var rows=jdbc.query("SELECT id,goal_id,name,description FROM suggested_activities WHERE id=? AND is_active=1",
            (rs,n)->new Activity(rs.getString("id"),rs.getString("goal_id"),rs.getString("name"),rs.getString("description")),request.activityId());
        if(rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Không tìm thấy hoạt động đang sử dụng.");
        jdbc.update("INSERT IGNORE INTO suggested_activity_goals(activity_id,goal_id) VALUES(?,?)",request.activityId(),goalId);
        return rows.getFirst();
    }

    @Transactional
    public Activity create(String goalId, CreateActivity request) {
        if (request == null || request.name() == null || request.name().isBlank() || request.name().strip().length() > 255)
            throw bad("Nhập tên hoạt động tối đa 255 ký tự.");
        String description = request.description() == null || request.description().isBlank() ? null : request.description().strip();
        if (description != null && description.length() > 10000) throw bad("Mô tả hoạt động quá dài.");
        Boolean exists = jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM goals WHERE id=? AND is_active=1)",Boolean.class,goalId);
        if (!Boolean.TRUE.equals(exists)) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Không tìm thấy mục tiêu đang sử dụng.");
        String normalizedName=request.name().strip();
        var reusable=jdbc.query("SELECT id,goal_id,name,description FROM suggested_activities WHERE name=? AND description <=> ? AND is_active=1 ORDER BY id LIMIT 1",
            (rs,n)->new Activity(rs.getString("id"),rs.getString("goal_id"),rs.getString("name"),rs.getString("description")),normalizedName,description);
        if(!reusable.isEmpty()) {
            var activity=reusable.getFirst();
            jdbc.update("INSERT IGNORE INTO suggested_activity_goals(activity_id,goal_id) VALUES(?,?)",activity.id(),goalId);
            return activity;
        }
        String id=UUID.randomUUID().toString();
        try {
            jdbc.update("INSERT INTO suggested_activities(id,goal_id,name,description) VALUES(?,?,?,?)",id,goalId,normalizedName,description);
        } catch (DuplicateKeyException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,"Hoạt động này đã có trong thư viện của mục tiêu.",ex);
        }
        jdbc.update("INSERT INTO suggested_activity_goals(activity_id,goal_id) VALUES(?,?)",id,goalId);
        return new Activity(id,goalId,normalizedName,description);
    }

    public List<ActivitySelection> resolve(String goalId,List<ActivityInput> inputs) {
        if(inputs==null || inputs.isEmpty()) return List.of();
        if(inputs.size()>50) throw bad("Mỗi mục tiêu chọn tối đa 50 hoạt động.");
        var seen=new HashSet<String>();
        var result=new ArrayList<ActivitySelection>();
        for(var input:inputs) {
            if(input==null || input.activityId()==null || !seen.add(input.activityId())) throw bad("Danh sách hoạt động không hợp lệ hoặc bị trùng.");
            String note=input.note()==null || input.note().isBlank()?null:input.note().strip();
            if(note!=null && note.length()>10000) throw bad("Ghi chú hoạt động quá dài.");
            var activity=jdbc.query("SELECT a.id,a.goal_id,a.name,a.description FROM suggested_activities a " +
                "LEFT JOIN suggested_activity_goals link ON link.activity_id=a.id AND link.goal_id=? " +
                "WHERE a.id=? AND (link.goal_id IS NOT NULL OR a.goal_id IS NULL) AND a.is_active=1",
                (rs,n)->new Activity(rs.getString("id"),rs.getString("goal_id"),rs.getString("name"),rs.getString("description")),goalId,input.activityId());
            if(activity.isEmpty()) throw bad("Hoạt động không thuộc mục tiêu này hoặc đã ngừng sử dụng.");
            var row=activity.getFirst();
            result.add(new ActivitySelection(row.id(),row.name(),row.description(),note));
        }
        return List.copyOf(result);
    }

    public void save(String planGoalId,List<ActivitySelection> selections) {
        for(int i=0;i<selections.size();i++) {
            var row=selections.get(i);
            jdbc.update("INSERT INTO plan_goal_activities(id,plan_goal_id,activity_id,name_snapshot,description_snapshot,note,display_order) VALUES(?,?,?,?,?,?,?)",
                UUID.randomUUID().toString(),planGoalId,row.activityId(),row.name(),row.description(),row.note(),i);
        }
    }

    public boolean matches(String planGoalId,List<ActivityInput> inputs) {
        var saved=jdbc.query("SELECT activity_id,note FROM plan_goal_activities WHERE plan_goal_id=? ORDER BY display_order,id",
            (rs,n)->new ActivityInput(rs.getString("activity_id"),rs.getString("note")),planGoalId);
        if(inputs==null) return saved.isEmpty();
        return saved.equals(inputs.stream().map(row->new ActivityInput(row.activityId(),row.note()==null || row.note().isBlank()?null:row.note().strip())).toList());
    }

    private ResponseStatusException bad(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST,message); }
}

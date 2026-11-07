package com.example.TTGDHNDuongAn.service;

import com.example.TTGDHNDuongAn.dto.GoalSelectionDtos.*;
import com.example.TTGDHNDuongAn.repository.ShortIds;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class StrategyService {
    public record Strategy(String id, String name, String description) {}
    private final JdbcTemplate jdbc;
    public StrategyService(JdbcTemplate jdbc) { this.jdbc=jdbc; }

    public List<Strategy> active() {
        return jdbc.query("SELECT id,name,description FROM strategies WHERE is_active=1 ORDER BY display_order,id",
            (rs,n)->new Strategy(rs.getString("id"),rs.getString("name"),rs.getString("description")));
    }

    public List<StrategySelection> resolve(List<StrategyInput> input) {
        var normalized=normalize(input);
        if(normalized.isEmpty()) return List.of();
        var available=active();
        var resolved=new ArrayList<StrategySelection>();
        for(var strategy:available) {
            if(normalized.containsKey(strategy.id()))
                resolved.add(new StrategySelection(strategy.id(),strategy.name(),normalized.get(strategy.id())));
        }
        if(resolved.size()!=normalized.size()) throw bad("Chiến lược đã ngừng sử dụng hoặc không tồn tại. Vui lòng chọn lại.");
        return List.copyOf(resolved);
    }

    private Map<String,String> normalize(List<StrategyInput> input) {
        var result=new LinkedHashMap<String,String>();
        if(input==null) return result;
        if(input.size()>50) throw bad("Mỗi mục tiêu chọn tối đa 50 chiến lược.");
        for(var row:input) {
            if(row==null || row.strategyId()==null || !row.strategyId().matches("CL[0-9]{3,6}") || result.containsKey(row.strategyId()))
                throw bad("Danh sách chiến lược không hợp lệ hoặc bị trùng.");
            String text=row.description()==null?null:row.description().strip();
            if(text!=null && text.isBlank()) text=null;
            if(text!=null && text.getBytes(StandardCharsets.UTF_8).length>65535)
                throw bad("Cách áp dụng chiến lược vượt quá độ dài cho phép.");
            result.put(row.strategyId(),text);
        }
        return result;
    }

    public List<StrategySelection> forGoal(String goal) {
        return jdbc.query("SELECT p.strategy_id,s.name,p.description FROM plan_goal_strategies p " +
            "JOIN strategies s ON s.id=p.strategy_id WHERE p.plan_goal_id=? ORDER BY p.display_order,p.id",
            (rs,n)->new StrategySelection(rs.getString("strategy_id"),rs.getString("name"),rs.getString("description")),goal);
    }

    public boolean matches(String goal,List<StrategyInput> input) {
        var saved=new LinkedHashMap<String,String>();
        for(var strategy:forGoal(goal)) saved.put(strategy.strategyId(),strategy.description());
        return saved.equals(normalize(input));
    }

    public void save(String goal,List<StrategySelection> selections) {
        if(selections.isEmpty()) return;
        // Lock selected catalog rows while the enclosing plan transaction commits.
        var ids=selections.stream().map(StrategySelection::strategyId).sorted().toList();
        var active=jdbc.queryForList("SELECT id FROM strategies WHERE is_active=1 AND id IN (" +
            String.join(",",Collections.nCopies(ids.size(),"?"))+") ORDER BY id FOR SHARE",String.class,ids.toArray());
        if(active.size()!=ids.size()) throw bad("Chiến lược đã thay đổi. Vui lòng kiểm tra lại kế hoạch.");
        for(int index=0;index<selections.size();index++) {
            var strategy=selections.get(index);
            jdbc.update("INSERT INTO plan_goal_strategies(id,plan_goal_id,strategy_id,description,display_order) VALUES(?,?,?,?,?)",
                ShortIds.next(jdbc,"plan_goal_strategies"),goal,strategy.strategyId(),strategy.description(),index);
        }
    }

    private ResponseStatusException bad(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST,message); }
}

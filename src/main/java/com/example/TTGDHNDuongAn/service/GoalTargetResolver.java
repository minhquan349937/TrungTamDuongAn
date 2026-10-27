package com.example.TTGDHNDuongAn.service;

import com.example.TTGDHNDuongAn.dto.GoalBankDtos.Selection;
import com.example.TTGDHNDuongAn.dto.GoalBankDtos.Target;
import com.example.TTGDHNDuongAn.dto.GoalCatalogDtos.GoalDetail;
import java.util.List;
import java.util.HashMap;
import java.util.ArrayList;
import com.example.TTGDHNDuongAn.repository.CatalogIdGenerator.Kind;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class GoalTargetResolver {
    private final GoalCatalogService catalog;
    public GoalTargetResolver(GoalCatalogService catalog) { this.catalog = catalog; }

    public Selection resolve(String goalId, String specificGoalId) {
        return resolve(catalog.goal(goalId),specificGoalId);
    }

    public List<Selection> resolveAll(List<Target> requests) {
        if(requests.stream().anyMatch(java.util.Objects::isNull))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid goal selection");
        var loaded=catalog.details(requests.stream().map(Target::goalId).distinct().toList());
        var result=new ArrayList<Selection>();
        for(var target:requests) {
            if(target==null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Mục tiêu không hợp lệ.");
            var goal=loaded.get(target.goalId());
            result.add(resolve(goal,target.specificGoalId()));
        }
        // Review/save follow the library outline, independent of checkbox click order.
        var mainOrder=new java.util.HashMap<String,Integer>();
        var childOrder=new java.util.HashMap<String,Integer>();
        for(var goal:loaded.values()) {
            mainOrder.put(goal.id(),mainOrder.size());
            for(int index=0;index<goal.specificGoals().size();index++)
                childOrder.put(goal.specificGoals().get(index).id(),index);
        }
        result.sort(java.util.Comparator.comparingInt((Selection row)->mainOrder.get(row.goalId()))
            .thenComparingInt(row->row.specificGoalId()==null?-1:childOrder.get(row.specificGoalId())));
        return result;
    }

    private Selection resolve(GoalDetail goal, String specificGoalId) {
        if (specificGoalId == null) {
            if (!goal.canSelectGeneralGoal())
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Vui lòng chọn mục tiêu cụ thể của mục tiêu này.");
            return new Selection(goal.id(),null,"GOAL",goal.code(),goal.name(),goal.description(),
                    goal.location(),goal.location().ageMinMonths(),goal.location().ageMaxMonths());
        }
        Kind.SPECIFIC.validate(specificGoalId);
        if (!goal.supportsSpecificGoals())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Chương trình này chỉ chọn mục tiêu chính.");
        var child=goal.specificGoals().stream().filter(row -> row.id().equals(specificGoalId)).findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT,"Mục tiêu cụ thể không thuộc mục tiêu cha hoặc đã ngừng sử dụng."));
        return new Selection(goal.id(),child.id(),"SPECIFIC_GOAL",child.code(),goal.name(),child.description(),
                goal.location(),child.ageMinMonths(),child.ageMaxMonths());
    }
}

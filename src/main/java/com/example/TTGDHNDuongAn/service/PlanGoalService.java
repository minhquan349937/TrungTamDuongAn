package com.example.TTGDHNDuongAn.service;

import com.example.TTGDHNDuongAn.repository.ShortIds;
import java.util.List;

import com.example.TTGDHNDuongAn.dto.PlanGoalDtos;
import com.example.TTGDHNDuongAn.dto.GoalCatalogDtos;
import com.example.TTGDHNDuongAn.dto.GoalSelectionDtos.Target;
import com.example.TTGDHNDuongAn.repository.PlanGoalRepository;
import com.example.TTGDHNDuongAn.repository.CatalogIdGenerator.Kind;
import com.example.TTGDHNDuongAn.security.AppUserPrincipal;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PlanGoalService {
    private final PlanGoalRepository repository;
    private final GoalTargetResolver targets;
    private final PlanAccessService access;
    private final GoalCatalogService catalog;
    private final StudentGoalHistoryService history;
    private final StrategyService strategies;
    private final SuggestedActivityService activities;

    public PlanGoalService(PlanGoalRepository repository, GoalTargetResolver targets, PlanAccessService access,
            GoalCatalogService catalog,StudentGoalHistoryService history,StrategyService strategies,SuggestedActivityService activities) {
        this.repository = repository;
        this.targets=targets;
        this.access=access;
        this.catalog=catalog;
        this.history=history;
        this.strategies=strategies;
        this.activities=activities;
    }

    @Transactional(readOnly=true)
    public GoalCatalogDtos.Page<GoalCatalogDtos.Goal> availableGoals(
            String planId,String domain,String query,int page,int size,AppUserPrincipal actor) {
        return catalog.availableGoals(requirePlan(planId,actor),domain,query,page,size);
    }

    @Transactional(readOnly=true)
    public GoalCatalogDtos.GoalDetail availableGoal(String planId,String goal,AppUserPrincipal actor) {
        String student=requirePlan(planId,actor);
        var detail=catalog.goal(goal);
        var evaluated=history.evaluatedKeys(student);
        var children=detail.specificGoals().stream().filter(c->!evaluated.contains("S:"+c.id())).toList();
        boolean general=detail.canSelectGeneralGoal()&&!evaluated.contains("G:"+goal);
        if(!general && children.isEmpty()) throw new ResponseStatusException(HttpStatus.CONFLICT,"Mục tiêu đã được đánh giá, không thể thêm lại.");
        return new GoalCatalogDtos.GoalDetail(detail.id(),detail.code(),detail.name(),detail.description(),
            children,detail.supportsSpecificGoals(),general,detail.location());
    }

    public List<PlanGoalDtos.AssignedGoal> list(String planId,AppUserPrincipal actor) {
        requirePlan(planId,actor);
        return repository.list(planId);
    }

    @Transactional(isolation=Isolation.READ_COMMITTED)
    public PlanGoalDtos.AssignedGoal assign(String planId, PlanGoalDtos.AssignGoal request, AppUserPrincipal actor) {
        String student=requirePlan(planId,actor);
        repository.lockStudent(student);
        if(repository.lockPlanStudent(planId)==null) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Plan not found");
        if (request == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Goal is required");
        Kind.GOAL.validate(request.goalId());
        if (request.specificGoalId() != null) Kind.SPECIFIC.validate(request.specificGoalId());
        targets.resolve(request.goalId(),request.specificGoalId());
        history.requireSelectable(student,List.of(new Target(request.goalId(),request.specificGoalId())));
        if (!repository.activeTargetExists(request.goalId(), request.specificGoalId()))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Goal inactive, missing, or specific goal belongs to another parent");
        var chosen=strategies.resolve(request.strategies());
        var chosenActivities=activities.resolve(request.goalId(),request.activities());
        try {
            String id = repository.insert(planId, request.goalId(), request.specificGoalId(), actor.id());
            strategies.save(id,chosen);
            activities.save(id,chosenActivities);
            return repository.list(planId).stream().filter(goal -> goal.id().equals(id)).findFirst().orElseThrow();
        } catch (DataIntegrityViolationException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Goal already assigned or cannot be assigned", ex);
        }
    }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public PlanGoalDtos.AssignedGoal replace(String planId,String oldRowId,PlanGoalDtos.AssignGoal request,AppUserPrincipal actor) {
        validateId(planId);validateId(oldRowId);
        String student=requirePlan(planId,actor);
        repository.lockStudent(student);
        if(repository.lockPlanStudent(planId)==null) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Plan not found");
        String oldTarget=repository.lockCurrentTarget(planId,oldRowId);
        if(oldTarget==null) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Current goal not found in plan");
        if(repository.hasEvaluations(oldRowId))
            throw new ResponseStatusException(HttpStatus.CONFLICT,"Mục tiêu đã được đánh giá, không thể thay mục tiêu.");
        if(request==null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Goal is required");
        Kind.GOAL.validate(request.goalId());
        if(request.specificGoalId()!=null) Kind.SPECIFIC.validate(request.specificGoalId());
        targets.resolve(request.goalId(),request.specificGoalId());
        history.requireSelectable(student,List.of(new Target(request.goalId(),request.specificGoalId())));
        if(!repository.activeTargetExists(request.goalId(),request.specificGoalId()))
            throw new ResponseStatusException(HttpStatus.CONFLICT,"Goal inactive or missing");
        if(oldTarget.equals(request.goalId()+":"+(request.specificGoalId()==null?"":request.specificGoalId())))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Choose a different goal");
        if(repository.currentTargetExists(planId,request.goalId(),request.specificGoalId()))
            throw new ResponseStatusException(HttpStatus.CONFLICT,"Goal already active in plan");
        var chosen=strategies.resolve(request.strategies());
        var chosenActivities=activities.resolve(request.goalId(),request.activities());
        try {
            String id=repository.insertReplacement(planId,oldRowId,request.goalId(),request.specificGoalId(),actor.id());
            strategies.save(id,chosen);
            activities.save(id,chosenActivities);
            return repository.list(planId).stream().filter(goal->goal.id().equals(id)).findFirst().orElseThrow();
        } catch(DataIntegrityViolationException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,"Goal cannot be replaced",ex);
        }
    }

    private String requirePlan(String planId,AppUserPrincipal actor) {
        validateId(planId);
        String student=repository.planStudent(planId);
        if (student==null)
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Plan not found");
        access.requireStudent(student,actor);
        return student;
    }

    private void validateId(String id) {
        try {
            if(!ShortIds.isId(id)) throw new IllegalArgumentException();
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid ID", ex);
        }
    }
}

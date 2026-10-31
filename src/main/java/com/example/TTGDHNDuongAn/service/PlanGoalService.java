package com.example.TTGDHNDuongAn.service;

import java.util.List;
import java.util.UUID;

import com.example.TTGDHNDuongAn.dto.PlanGoalDtos;
import com.example.TTGDHNDuongAn.repository.PlanGoalRepository;
import com.example.TTGDHNDuongAn.repository.CatalogIdGenerator.Kind;
import com.example.TTGDHNDuongAn.security.AppUserPrincipal;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PlanGoalService {
    private final PlanGoalRepository repository;
    private final GoalTargetResolver targets;
    private final PlanAccessService access;

    public PlanGoalService(PlanGoalRepository repository, GoalTargetResolver targets, PlanAccessService access) {
        this.repository = repository;
        this.targets=targets;
        this.access=access;
    }

    public List<PlanGoalDtos.AssignedGoal> list(String planId,AppUserPrincipal actor) {
        requirePlan(planId,actor);
        return repository.list(planId);
    }

    @Transactional
    public PlanGoalDtos.AssignedGoal assign(String planId, PlanGoalDtos.AssignGoal request, AppUserPrincipal actor) {
        requirePlan(planId,actor);
        if (request == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Goal is required");
        Kind.GOAL.validate(request.goalId());
        if (request.specificGoalId() != null) Kind.SPECIFIC.validate(request.specificGoalId());
        targets.resolve(request.goalId(),request.specificGoalId());
        if (!repository.activeTargetExists(request.goalId(), request.specificGoalId()))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Goal inactive, missing, or specific goal belongs to another parent");
        try {
            String id = repository.insert(planId, request.goalId(), request.specificGoalId(), actor.id());
            return repository.list(planId).stream().filter(goal -> goal.id().equals(id)).findFirst().orElseThrow();
        } catch (DataIntegrityViolationException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Goal already assigned or cannot be assigned", ex);
        }
    }
    @Transactional
    public PlanGoalDtos.AssignedGoal replace(String planId,String oldRowId,PlanGoalDtos.AssignGoal request,AppUserPrincipal actor) {
        validateId(planId);validateId(oldRowId);
        String student=repository.lockPlanStudent(planId);
        if(student==null) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Plan not found");
        access.requireStudent(student,actor);
        String oldTarget=repository.lockCurrentTarget(planId,oldRowId);
        if(oldTarget==null) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Current goal not found in plan");
        if(request==null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Goal is required");
        Kind.GOAL.validate(request.goalId());
        if(request.specificGoalId()!=null) Kind.SPECIFIC.validate(request.specificGoalId());
        targets.resolve(request.goalId(),request.specificGoalId());
        if(!repository.activeTargetExists(request.goalId(),request.specificGoalId()))
            throw new ResponseStatusException(HttpStatus.CONFLICT,"Goal inactive or missing");
        if(oldTarget.equals(request.goalId()+":"+(request.specificGoalId()==null?"":request.specificGoalId())))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Choose a different goal");
        if(repository.currentTargetExists(planId,request.goalId(),request.specificGoalId()))
            throw new ResponseStatusException(HttpStatus.CONFLICT,"Goal already active in plan");
        try {
            String id=repository.insertReplacement(planId,oldRowId,request.goalId(),request.specificGoalId(),actor.id());
            return repository.list(planId).stream().filter(goal->goal.id().equals(id)).findFirst().orElseThrow();
        } catch(DataIntegrityViolationException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,"Goal cannot be replaced",ex);
        }
    }

    private void requirePlan(String planId,AppUserPrincipal actor) {
        validateId(planId);
        String student=repository.planStudent(planId);
        if (student==null)
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Plan not found");
        access.requireStudent(student,actor);
    }

    private void validateId(String id) {
        try {
            UUID.fromString(id);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid UUID", ex);
        }
    }
}

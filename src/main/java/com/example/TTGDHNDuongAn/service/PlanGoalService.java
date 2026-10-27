package com.example.TTGDHNDuongAn.service;

import java.util.List;
import java.util.UUID;

import com.example.TTGDHNDuongAn.dto.PlanGoalDtos;
import com.example.TTGDHNDuongAn.repository.PlanGoalRepository;
import com.example.TTGDHNDuongAn.repository.CatalogIdGenerator.Kind;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PlanGoalService {
    private final PlanGoalRepository repository;
    private final GoalTargetResolver targets;

    public PlanGoalService(PlanGoalRepository repository, GoalTargetResolver targets) {
        this.repository = repository;
        this.targets=targets;
    }

    public List<PlanGoalDtos.AssignedGoal> list(String planId) {
        requirePlan(planId);
        return repository.list(planId);
    }

    @Transactional
    public PlanGoalDtos.AssignedGoal assign(String planId, PlanGoalDtos.AssignGoal request, String actorId) {
        requirePlan(planId);
        if (request == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Goal is required");
        Kind.GOAL.validate(request.goalId());
        if (request.specificGoalId() != null) Kind.SPECIFIC.validate(request.specificGoalId());
        targets.resolve(request.goalId(),request.specificGoalId());
        if (!repository.activeTargetExists(request.goalId(), request.specificGoalId()))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Goal inactive, missing, or specific goal belongs to another parent");
        try {
            String id = repository.insert(planId, request.goalId(), request.specificGoalId(), actorId);
            return repository.list(planId).stream().filter(goal -> goal.id().equals(id)).findFirst().orElseThrow();
        } catch (DataIntegrityViolationException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Goal already assigned or cannot be assigned", ex);
        }
    }

    private void requirePlan(String planId) {
        validateId(planId);
        if (!repository.planExists(planId))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Plan not found");
    }

    private void validateId(String id) {
        try {
            UUID.fromString(id);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid UUID", ex);
        }
    }
}

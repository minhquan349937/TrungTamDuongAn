package com.example.TTGDHNDuongAn.controller;

import java.net.URI;
import java.util.List;

import com.example.TTGDHNDuongAn.dto.PlanGoalDtos;
import com.example.TTGDHNDuongAn.security.AppUserPrincipal;
import com.example.TTGDHNDuongAn.service.PlanGoalService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PlanGoalController {
    private final PlanGoalService service;

    public PlanGoalController(PlanGoalService service) {
        this.service = service;
    }

    @GetMapping("/api/plans/{id}/goals")
    public List<PlanGoalDtos.AssignedGoal> list(@PathVariable String id) {
        return service.list(id);
    }

    @PostMapping("/api/plans/{id}/goals")
    public ResponseEntity<PlanGoalDtos.AssignedGoal> assign(
            @PathVariable String id, @RequestBody PlanGoalDtos.AssignGoal request, Authentication authentication) {
        AppUserPrincipal actor = (AppUserPrincipal) authentication.getPrincipal();
        PlanGoalDtos.AssignedGoal assigned = service.assign(id, request, actor.id());
        return ResponseEntity.created(URI.create("/api/plans/" + id + "/goals/" + assigned.id())).body(assigned);
    }
}

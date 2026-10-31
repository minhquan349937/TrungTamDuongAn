package com.example.TTGDHNDuongAn.controller;

import com.example.TTGDHNDuongAn.dto.GoalEvaluationDtos.*;
import com.example.TTGDHNDuongAn.service.GoalEvaluationService;
import com.example.TTGDHNDuongAn.security.AppUserPrincipal;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/plans/{plan}/goals/{goal}/evaluations")
public class GoalEvaluationController {
    private final GoalEvaluationService service;
    public GoalEvaluationController(GoalEvaluationService service) { this.service=service; }
    @GetMapping public List<Evaluation> list(@PathVariable String plan,@PathVariable String goal,Authentication auth) {
        return service.list(plan,goal,(AppUserPrincipal)auth.getPrincipal());
    }
    @PostMapping public List<Evaluation> create(@PathVariable String plan,@PathVariable String goal,@RequestBody Input input,Authentication auth) {
        return service.create(plan,goal,input,(AppUserPrincipal)auth.getPrincipal());
    }
    @PutMapping("/{id}") public List<Evaluation> update(@PathVariable String plan,@PathVariable String goal,@PathVariable String id,
        @RequestBody Input input,Authentication auth) {
        return service.update(plan,goal,id,input,(AppUserPrincipal)auth.getPrincipal());
    }
}

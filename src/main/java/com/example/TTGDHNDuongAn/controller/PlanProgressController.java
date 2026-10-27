package com.example.TTGDHNDuongAn.controller;

import com.example.TTGDHNDuongAn.dto.PlanProgressDtos.*;
import com.example.TTGDHNDuongAn.service.PlanProgressService;
import com.example.TTGDHNDuongAn.security.AppUserPrincipal;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
public class PlanProgressController {
    private final PlanProgressService service;
    public PlanProgressController(PlanProgressService service) { this.service=service; }
    @PatchMapping("/api/plans/{id}") @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
    public void edit(@PathVariable String id,@RequestBody EditPlan request) { service.edit(id,request); }
    @PatchMapping("/api/plans/{id}/goals/{goal}") @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
    public void status(@PathVariable String id,@PathVariable String goal,@RequestBody GoalStatus request) { service.status(id,goal,request); }
    @GetMapping("/api/plans/{id}/goals/{goal}/progress")
    public List<Progress> progress(@PathVariable String id,@PathVariable String goal) { return service.list(id,goal); }
    @PostMapping("/api/plans/{id}/goals/{goal}/progress")
    public List<Progress> observe(@PathVariable String id,@PathVariable String goal,@RequestBody Observation request,Authentication auth) {
        return service.observe(id,goal,request,((AppUserPrincipal)auth.getPrincipal()).id());
    }
}

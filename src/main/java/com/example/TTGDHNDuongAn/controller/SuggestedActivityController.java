package com.example.TTGDHNDuongAn.controller;

import com.example.TTGDHNDuongAn.service.SuggestedActivityService;
import com.example.TTGDHNDuongAn.service.SuggestedActivityService.Activity;
import com.example.TTGDHNDuongAn.service.SuggestedActivityService.CreateActivity;
import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/suggested-activities")
public class SuggestedActivityController {
    private final SuggestedActivityService service;
    public SuggestedActivityController(SuggestedActivityService service) { this.service=service; }
    @GetMapping("/goal-search") public List<SuggestedActivityService.GoalOption> searchGoals(@RequestParam(defaultValue="") String q) {
        return service.searchGoals(q);
    }
    @GetMapping("/activities") public List<Activity> searchActivities(@RequestParam(defaultValue="") String q) {
        return service.searchActivities(q);
    }
    @PostMapping("/activities") public Activity createLibrary(@RequestBody CreateActivity request) {
        return service.createLibrary(request);
    }
    @GetMapping("/goals/{goalId}") public List<Activity> list(@PathVariable String goalId) { return service.forGoal(goalId); }
    @PostMapping("/goals/{goalId}") public Activity create(@PathVariable String goalId,@RequestBody CreateActivity request) {
        return service.create(goalId,request);
    }
    @PostMapping("/goals/{goalId}/links") public Activity link(@PathVariable String goalId,@RequestBody SuggestedActivityService.LinkActivity request) {
        return service.link(goalId,request);
    }
}

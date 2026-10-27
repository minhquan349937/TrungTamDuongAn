package com.example.TTGDHNDuongAn.controller;

import java.net.URI;
import java.util.List;

import com.example.TTGDHNDuongAn.dto.GoalCatalogDtos;
import com.example.TTGDHNDuongAn.service.GoalCatalogService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class GoalCatalogController {
    private final GoalCatalogService service;

    public GoalCatalogController(GoalCatalogService service) {
        this.service = service;
    }

    @GetMapping("/api/programs")
    public List<GoalCatalogDtos.Program> programs() {
        return service.programs();
    }

    @GetMapping("/api/programs/{id}/levels")
    public List<GoalCatalogDtos.ProgramLevel> levels(@PathVariable String id) {
        return service.levels(id);
    }

    @GetMapping("/api/programs/{id}/domains")
    public List<GoalCatalogDtos.ProgramDomain> programDomains(@PathVariable String id) { return service.programDomains(id); }
    @PostMapping("/api/programs/{id}/domains")
    public ResponseEntity<GoalCatalogDtos.ProgramDomain> programDomain(@PathVariable String id,@RequestBody GoalCatalogDtos.CreateDomain request) {
        return ResponseEntity.status(201).body(service.createProgramDomain(id,request));
    }
    @PostMapping("/api/levels/{id}/domain-links")
    public ResponseEntity<GoalCatalogDtos.Domain> link(@PathVariable String id,@RequestBody GoalCatalogDtos.LinkDomain request) {
        return ResponseEntity.status(201).body(service.linkDomain(id,request));
    }

    @GetMapping("/api/levels/{id}/domains")
    public List<GoalCatalogDtos.Domain> domains(@PathVariable String id) {
        return service.domains(id);
    }

    @GetMapping("/api/level-domains/{id}/goals")
    public GoalCatalogDtos.Page<GoalCatalogDtos.Goal> goals(
            @PathVariable String id,
            @RequestParam(defaultValue = "") String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.goals(id, q, page, size);
    }

    @GetMapping("/api/goals/{id}")
    public GoalCatalogDtos.GoalDetail goal(@PathVariable String id) {
        return service.goal(id);
    }

    @PostMapping("/api/level-domains/{id}/goals")
    public ResponseEntity<GoalCatalogDtos.GoalDetail> createGoal(
            @PathVariable String id, @RequestBody GoalCatalogDtos.CreateGoal request) {
        GoalCatalogDtos.GoalDetail goal = service.createGoal(id, request);
        return ResponseEntity.created(URI.create("/api/goals/" + goal.id())).body(goal);
    }

    @PostMapping("/api/programs")
    public ResponseEntity<GoalCatalogDtos.Program> createProgram(@RequestBody GoalCatalogDtos.CreateProgram request) {
        var row = service.createProgram(request);
        return ResponseEntity.created(URI.create("/api/programs/" + row.id() + "/levels")).body(row);
    }

    @PostMapping("/api/programs/{id}/levels")
    public ResponseEntity<GoalCatalogDtos.ProgramLevel> createLevel(@PathVariable String id, @RequestBody GoalCatalogDtos.CreateLevel request) {
        var row = service.createLevel(id, request);
        return ResponseEntity.created(URI.create("/api/levels/" + row.id() + "/domains")).body(row);
    }

    @PostMapping("/api/levels/{id}/domains")
    public ResponseEntity<GoalCatalogDtos.Domain> createDomain(@PathVariable String id, @RequestBody GoalCatalogDtos.CreateDomain request) {
        var row = service.createDomain(id, request);
        return ResponseEntity.created(URI.create("/api/level-domains/" + row.levelDomainId() + "/goals")).body(row);
    }

    @PostMapping("/api/goals/{id}/specific-goals")
    public ResponseEntity<GoalCatalogDtos.GoalDetail> createSpecific(@PathVariable String id, @RequestBody GoalCatalogDtos.WriteSpecificGoal request) {
        return ResponseEntity.status(201).body(service.createSpecific(id, request));
    }

    @PutMapping("/api/goals/{id}")
    public GoalCatalogDtos.GoalDetail updateGoal(@PathVariable String id, @RequestBody GoalCatalogDtos.CreateGoal request) {
        return service.updateGoal(id, request);
    }

    @PutMapping("/api/goals/{id}/specific-goals/{specificId}")
    public GoalCatalogDtos.GoalDetail updateSpecific(@PathVariable String id, @PathVariable String specificId,
                                                  @RequestBody GoalCatalogDtos.WriteSpecificGoal request) {
        return service.updateSpecific(id, specificId, request);
    }

    @DeleteMapping("/api/goals/{id}")
    public ResponseEntity<Void> archiveGoal(@PathVariable String id) {
        service.archiveGoal(id); return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/api/goals/{id}/specific-goals/{specificId}")
    public ResponseEntity<Void> archiveSpecific(@PathVariable String id, @PathVariable String specificId) {
        service.archiveSpecific(id, specificId); return ResponseEntity.noContent().build();
    }
}

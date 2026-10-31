package com.example.TTGDHNDuongAn.controller;

import java.util.List;
import com.example.TTGDHNDuongAn.dto.InterventionDtos.*;
import com.example.TTGDHNDuongAn.security.AppUserPrincipal;
import com.example.TTGDHNDuongAn.service.InterventionService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/intervention")
public class InterventionController {
    private final InterventionService service;
    private final com.example.TTGDHNDuongAn.service.PlanAccessService access;
    private final com.example.TTGDHNDuongAn.service.StudentGoalHistoryService history;
    public InterventionController(InterventionService service,com.example.TTGDHNDuongAn.service.PlanAccessService access,
        com.example.TTGDHNDuongAn.service.StudentGoalHistoryService history) { this.service=service; this.access=access; this.history=history; }
    @GetMapping("/students") public List<StudentSummary> students(@RequestParam(defaultValue="") String q,Authentication auth) {
        var actor=(AppUserPrincipal)auth.getPrincipal();
        return service.students(q).stream().filter(s->access.canReadStudent(s.id(),actor)).toList();
    }
    @GetMapping("/students/{id}") public Workspace workspace(@PathVariable String id,Authentication auth) {
        access.requireStudent(id,(AppUserPrincipal)auth.getPrincipal()); return service.workspace(id);
    }
    @GetMapping("/programs") public List<ProgramSummary> programs() { return service.programs(); }
    @GetMapping("/programs/{id}/domains") public List<DomainGroup> domains(@PathVariable String id) { return service.domains(id); }
    @GetMapping("/level-domains/{id}/targets") public DomainTargets targets(@PathVariable String id) { return service.targets(id); }
    @GetMapping("/students/{student}/level-domains/{id}/targets")
    public StudentDomainTargets studentTargets(@PathVariable String student,@PathVariable String id,Authentication auth) {
        access.requireStudent(student,(AppUserPrincipal)auth.getPrincipal());
        service.workspace(student); // validates the student ID and existence before reading history
        return history.targets(student,service.targets(id));
    }
    @PostMapping("/students/{id}/preview") public Review preview(@PathVariable String id,@RequestBody Draft draft) { return service.preview(id,draft); }
    @PostMapping("/students/{id}/complete") public Saved complete(@PathVariable String id,@RequestBody Complete request,Authentication authentication) {
        return service.complete(id,request,((AppUserPrincipal)authentication.getPrincipal()).id());
    }
}

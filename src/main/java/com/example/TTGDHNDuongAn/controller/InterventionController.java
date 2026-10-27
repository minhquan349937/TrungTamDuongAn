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
    public InterventionController(InterventionService service) { this.service=service; }
    @GetMapping("/students") public List<StudentSummary> students(@RequestParam(defaultValue="") String q) { return service.students(q); }
    @GetMapping("/students/{id}") public Workspace workspace(@PathVariable String id) { return service.workspace(id); }
    @GetMapping("/programs") public List<ProgramSummary> programs() { return service.programs(); }
    @GetMapping("/programs/{id}/domains") public List<DomainGroup> domains(@PathVariable String id) { return service.domains(id); }
    @GetMapping("/level-domains/{id}/targets") public DomainTargets targets(@PathVariable String id) { return service.targets(id); }
    @PostMapping("/students/{id}/preview") public Review preview(@PathVariable String id,@RequestBody Draft draft) { return service.preview(id,draft); }
    @PostMapping("/students/{id}/complete") public Saved complete(@PathVariable String id,@RequestBody Complete request,Authentication authentication) {
        return service.complete(id,request,((AppUserPrincipal)authentication.getPrincipal()).id());
    }
}

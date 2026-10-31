package com.example.TTGDHNDuongAn.controller;

import com.example.TTGDHNDuongAn.dto.PlanEvaluationDtos.*;
import com.example.TTGDHNDuongAn.security.AppUserPrincipal;
import com.example.TTGDHNDuongAn.service.PlanEvaluationService;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
public class PlanEvaluationController {
    private final PlanEvaluationService service;
    public PlanEvaluationController(PlanEvaluationService service){this.service=service;}
    private AppUserPrincipal actor(Authentication auth){return (AppUserPrincipal)auth.getPrincipal();}
    @PostMapping("/api/learning-plans/{planId}/evaluations")
    public ResponseEntity<Report> create(@PathVariable String planId,@RequestBody Input input,Authentication auth){
        Report row=service.create(planId,input,actor(auth));
        return ResponseEntity.created(URI.create("/api/plan-evaluations/"+row.id())).body(row);
    }
    @GetMapping("/api/learning-plans/{planId}/evaluations")
    public List<Report> list(@PathVariable String planId,Authentication auth){return service.list(planId,actor(auth));}
    @GetMapping("/api/plan-evaluations/{id}")
    public Report get(@PathVariable String id,Authentication auth){return service.get(id,actor(auth));}
    @PutMapping("/api/plan-evaluations/{id}")
    public Report update(@PathVariable String id,@RequestBody Input input,Authentication auth){return service.update(id,input,actor(auth));}
    @PostMapping("/api/plan-evaluations/{id}/send")
    public Report publish(@PathVariable String id,Authentication auth){return service.publish(id,actor(auth));}
    @GetMapping("/api/parent/plan-evaluations")
    public List<Report> parentReports(Authentication auth){return service.parentReports(actor(auth));}
}

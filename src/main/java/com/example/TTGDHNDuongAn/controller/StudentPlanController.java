package com.example.TTGDHNDuongAn.controller;

import java.net.URI;
import java.util.List;

import com.example.TTGDHNDuongAn.dto.StudentPlanDtos;
import com.example.TTGDHNDuongAn.security.AppUserPrincipal;
import com.example.TTGDHNDuongAn.service.StudentPlanService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class StudentPlanController {
    private final StudentPlanService service;

    public StudentPlanController(StudentPlanService service) {
        this.service = service;
    }

    @GetMapping("/api/students")
    public List<StudentPlanDtos.Student> students(@RequestParam(defaultValue = "") String q) {
        return service.students(q);
    }

    @PostMapping("/api/students")
    public ResponseEntity<StudentPlanDtos.Student> createStudent(@RequestBody StudentPlanDtos.CreateStudent request) {
        StudentPlanDtos.Student student = service.createStudent(request);
        return ResponseEntity.created(URI.create("/api/students/" + student.id())).body(student);
    }

    @GetMapping("/api/students/{id}/plans")
    public List<StudentPlanDtos.Plan> plans(@PathVariable String id) {
        return service.plans(id);
    }

    @PostMapping("/api/students/{id}/plans")
    public ResponseEntity<StudentPlanDtos.Plan> createPlan(
            @PathVariable String id, @RequestBody StudentPlanDtos.CreatePlan request, Authentication authentication) {
        AppUserPrincipal actor = (AppUserPrincipal) authentication.getPrincipal();
        StudentPlanDtos.Plan plan = service.createPlan(id, request, actor.id());
        return ResponseEntity.created(URI.create("/api/plans/" + plan.id())).body(plan);
    }
}

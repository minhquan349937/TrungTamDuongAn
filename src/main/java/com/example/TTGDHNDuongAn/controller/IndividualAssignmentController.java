package com.example.TTGDHNDuongAn.controller;

import com.example.TTGDHNDuongAn.service.IndividualAssignmentService;
import com.example.TTGDHNDuongAn.service.IndividualAssignmentService.*;
import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/individual-assignments")
public class IndividualAssignmentController {
    private final IndividualAssignmentService service;
    public IndividualAssignmentController(IndividualAssignmentService service) { this.service=service; }
    @GetMapping public List<Assignment> list() { return service.list(); }
    @GetMapping("/teachers") public List<Teacher> teachers() { return service.teachers(); }
    @PostMapping public Assignment create(@RequestBody Create input) { return service.create(input); }
    @PutMapping("/{id}") public Assignment change(@PathVariable String id,@RequestBody Change input) { return service.change(id,input); }
}

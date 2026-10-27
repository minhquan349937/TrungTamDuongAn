package com.example.TTGDHNDuongAn.controller;

import java.util.List;
import com.example.TTGDHNDuongAn.dto.GoalBankDtos;
import com.example.TTGDHNDuongAn.security.AppUserPrincipal;
import com.example.TTGDHNDuongAn.service.StudentGoalBankService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/students/{studentId}/goal-bank")
public class StudentGoalBankController {
    private final StudentGoalBankService service;
    public StudentGoalBankController(StudentGoalBankService service) { this.service=service; }
    @GetMapping
    public List<GoalBankDtos.BankEntry> list(@PathVariable String studentId) { return service.list(studentId); }
    @PostMapping("/preview")
    public GoalBankDtos.Review preview(@PathVariable String studentId,@RequestBody GoalBankDtos.ReviewRequest request) {
        return service.preview(studentId,request);
    }
    @PostMapping("/complete")
    public GoalBankDtos.Completion complete(@PathVariable String studentId,@RequestBody GoalBankDtos.CompleteRequest request,Authentication authentication) {
        return service.complete(studentId,request,((AppUserPrincipal)authentication.getPrincipal()).id());
    }
}

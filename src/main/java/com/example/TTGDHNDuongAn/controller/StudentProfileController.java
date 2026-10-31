package com.example.TTGDHNDuongAn.controller;

import com.example.TTGDHNDuongAn.service.StudentProfileService;
import com.example.TTGDHNDuongAn.service.StudentProfileService.Profile;
import com.example.TTGDHNDuongAn.service.StudentProfileService.ProfileInput;
import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/student-profiles")
public class StudentProfileController {
    private final StudentProfileService service;

    public StudentProfileController(StudentProfileService service) { this.service = service; }

    @GetMapping public List<Profile> list() { return service.list(); }
    @GetMapping("/{id}") public Profile get(@PathVariable String id) { return service.get(id); }
    @PostMapping public Profile create(@RequestBody ProfileInput input) { return service.create(input); }
    @PutMapping("/{id}") public Profile update(@PathVariable String id, @RequestBody ProfileInput input) {
        return service.update(id, input);
    }
}

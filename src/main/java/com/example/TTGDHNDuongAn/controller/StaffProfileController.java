package com.example.TTGDHNDuongAn.controller;

import com.example.TTGDHNDuongAn.service.StaffProfileService;
import com.example.TTGDHNDuongAn.service.StaffProfileService.Input;
import com.example.TTGDHNDuongAn.service.StaffProfileService.Profile;
import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/staff-profiles")
public class StaffProfileController {
    private final StaffProfileService service;
    public StaffProfileController(StaffProfileService service) { this.service = service; }
    @GetMapping public List<Profile> list() { return service.list(); }
    @PostMapping public Profile create(@RequestBody Input input) { return service.create(input); }
    @PutMapping("/{id}") public Profile update(@PathVariable String id, @RequestBody Input input) {
        return service.update(id,input);
    }
}

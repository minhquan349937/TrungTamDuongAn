package com.example.TTGDHNDuongAn.controller;

import com.example.TTGDHNDuongAn.service.GuardianProfileService;
import com.example.TTGDHNDuongAn.service.GuardianProfileService.Guardian;
import com.example.TTGDHNDuongAn.service.GuardianProfileService.GuardianInput;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/guardians")
public class GuardianProfileController {
    private final GuardianProfileService service;
    public GuardianProfileController(GuardianProfileService service) { this.service = service; }

    @PutMapping("/{id}")
    public Guardian update(@PathVariable String id, @RequestBody GuardianInput input) {
        return service.update(id, input);
    }
}

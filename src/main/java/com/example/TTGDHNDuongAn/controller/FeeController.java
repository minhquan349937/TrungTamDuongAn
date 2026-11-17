package com.example.TTGDHNDuongAn.controller;

import com.example.TTGDHNDuongAn.security.AppUserPrincipal;
import com.example.TTGDHNDuongAn.service.FeeService;
import com.example.TTGDHNDuongAn.service.FeeService.*;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/fees")
public class FeeController {
    private final FeeService service;
    public FeeController(FeeService service) { this.service = service; }
    private AppUserPrincipal actor(Authentication auth) { return (AppUserPrincipal)auth.getPrincipal(); }
    @GetMapping public List<Fee> list(Authentication auth) { return service.list(actor(auth)); }
    @GetMapping("/students") public List<Student> students(Authentication auth) { return service.students(actor(auth)); }
    @PostMapping public Fee create(Authentication auth,@RequestBody CreateFee input) { return service.create(actor(auth),input); }
    @PostMapping("/{id}/payment") public Fee pay(Authentication auth,@PathVariable String id) { return service.pay(actor(auth),id); }
    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<Map<String,String>> businessError(ResponseStatusException error) {
        return ResponseEntity.status(error.getStatusCode()).body(Map.of("message",error.getReason() == null ? "Không thể xử lý khoản thu." : error.getReason()));
    }
}

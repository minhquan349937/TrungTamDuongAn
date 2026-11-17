package com.example.TTGDHNDuongAn.controller;

import com.example.TTGDHNDuongAn.security.AppUserPrincipal;
import com.example.TTGDHNDuongAn.service.ScheduleService;
import java.time.LocalDate;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/schedule")
public class ScheduleController {
    private final ScheduleService service;
    public ScheduleController(ScheduleService service) {this.service=service;}
    @GetMapping public ScheduleService.Calendar get(@RequestParam LocalDate from,@RequestParam LocalDate to,Authentication auth) {return service.get(from,to,(AppUserPrincipal)auth.getPrincipal());}
    @PostMapping("/sessions") public Map<String,String> create(@RequestBody ScheduleService.Input input,Authentication auth) {return Map.of("id",service.save(null,input,(AppUserPrincipal)auth.getPrincipal()));}
    @PutMapping("/sessions/{id}") public Map<String,String> update(@PathVariable String id,@RequestBody ScheduleService.Input input,Authentication auth) {return Map.of("id",service.save(id,input,(AppUserPrincipal)auth.getPrincipal()));}
    @PutMapping("/sessions/{id}/attendance") @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
    public void attendance(@PathVariable String id,@RequestBody ScheduleService.AttendanceInput input,Authentication auth) {service.attendance(id,input,(AppUserPrincipal)auth.getPrincipal());}
    @ExceptionHandler(ResponseStatusException.class) ResponseEntity<Map<String,String>> error(ResponseStatusException ex) {return ResponseEntity.status(ex.getStatusCode()).body(Map.of("message",ex.getReason()==null?"Không thể xử lý lịch học.":ex.getReason()));}
}

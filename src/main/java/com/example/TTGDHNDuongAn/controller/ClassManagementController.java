package com.example.TTGDHNDuongAn.controller;

import com.example.TTGDHNDuongAn.service.ClassManagementService;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/admin/classes")
public class ClassManagementController {
    private final ClassManagementService service;
    public ClassManagementController(ClassManagementService service) {this.service=service;}
    @GetMapping public List<ClassManagementService.SchoolClass> list(@RequestParam String type) {return service.list(type);}
    @PostMapping public ClassManagementService.SchoolClass create(@RequestBody ClassManagementService.Input input) {return service.save(null,input);}
    @PutMapping("/{id}") public ClassManagementService.SchoolClass update(@PathVariable String id,@RequestBody ClassManagementService.Input input) {return service.save(id,input);}
    @DeleteMapping("/{id}") @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
    public void close(@PathVariable String id) {service.close(id);}
    @ExceptionHandler(ResponseStatusException.class) ResponseEntity<Map<String,String>> error(ResponseStatusException ex) {
        return ResponseEntity.status(ex.getStatusCode()).body(Map.of("message",ex.getReason()==null?"Không thể lưu lớp học.":ex.getReason()));
    }
}

package com.example.TTGDHNDuongAn.controller;

import com.example.TTGDHNDuongAn.security.AppUserPrincipal;
import com.example.TTGDHNDuongAn.service.TeacherPortalService;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/teacher/portal")
public class TeacherPortalController {
    private final TeacherPortalService service;
    public TeacherPortalController(TeacherPortalService service) {this.service=service;}
    @GetMapping public TeacherPortalService.Portal get(Authentication auth) {return service.get((AppUserPrincipal)auth.getPrincipal());}
    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<Map<String,String>> error(ResponseStatusException ex) {
        return ResponseEntity.status(ex.getStatusCode()).body(Map.of("message",ex.getReason()==null?"Không thể tải trang giáo viên.":ex.getReason()));
    }
}

package com.example.TTGDHNDuongAn.controller;

import com.example.TTGDHNDuongAn.security.AppUserPrincipal;
import com.example.TTGDHNDuongAn.service.ParentPortalService;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/parent/portal")
public class ParentPortalController {
    private final ParentPortalService service;
    public ParentPortalController(ParentPortalService service) { this.service=service; }
    @GetMapping public ParentPortalService.Portal get(Authentication auth) { return service.get((AppUserPrincipal)auth.getPrincipal()); }
    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<Map<String,String>> businessError(ResponseStatusException error) {
        return ResponseEntity.status(error.getStatusCode()).body(Map.of("message",error.getReason() == null ? "Không thể tải hồ sơ phụ huynh." : error.getReason()));
    }
}

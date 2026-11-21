package com.example.TTGDHNDuongAn.controller;

import com.example.TTGDHNDuongAn.security.AppUserPrincipal;
import com.example.TTGDHNDuongAn.service.PlanExcelService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PlanExcelController {
    private static final MediaType XLSX = MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
    private final PlanExcelService service;

    public PlanExcelController(PlanExcelService service) { this.service = service; }

    @GetMapping("/api/plans/{id}/excel")
    public ResponseEntity<byte[]> download(@PathVariable String id, Authentication authentication) {
        byte[] file = service.export(id, (AppUserPrincipal) authentication.getPrincipal());
        return ResponseEntity.ok().contentType(XLSX)
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=ke-hoach-" + id + ".xlsx")
            .body(file);
    }
}

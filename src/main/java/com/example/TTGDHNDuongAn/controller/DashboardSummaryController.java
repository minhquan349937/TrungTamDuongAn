package com.example.TTGDHNDuongAn.controller;

import com.example.TTGDHNDuongAn.service.DashboardSummaryService;
import com.example.TTGDHNDuongAn.service.DashboardSummaryService.Summary;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/dashboard-summary")
public class DashboardSummaryController {
    private final DashboardSummaryService service;

    public DashboardSummaryController(DashboardSummaryService service) {
        this.service = service;
    }

    @GetMapping
    public Summary getSummary() {
        return service.getSummary();
    }
}

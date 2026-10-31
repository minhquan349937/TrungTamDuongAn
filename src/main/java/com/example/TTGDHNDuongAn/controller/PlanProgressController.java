package com.example.TTGDHNDuongAn.controller;
import com.example.TTGDHNDuongAn.dto.PlanProgressDtos.EditPlan;
import com.example.TTGDHNDuongAn.dto.PlanProgressDtos.ChangeStatus;
import com.example.TTGDHNDuongAn.service.PlanProgressService;
import com.example.TTGDHNDuongAn.security.AppUserPrincipal;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
@RestController
public class PlanProgressController {
    private final PlanProgressService service;
    public PlanProgressController(PlanProgressService service) { this.service=service; }
    @PatchMapping("/api/plans/{id}") @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
    public void edit(@PathVariable String id,@RequestBody EditPlan request,Authentication auth) { service.edit(id,request,(AppUserPrincipal)auth.getPrincipal()); }
    @PatchMapping("/api/plans/{id}/status") @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
    public void changeStatus(@PathVariable String id,@RequestBody ChangeStatus request,Authentication auth) {
        service.changeStatus(id,request,(AppUserPrincipal)auth.getPrincipal());
    }
}

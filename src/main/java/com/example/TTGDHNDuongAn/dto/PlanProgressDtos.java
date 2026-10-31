package com.example.TTGDHNDuongAn.dto;
import java.time.LocalDate;
public final class PlanProgressDtos {
    private PlanProgressDtos() {}
    public record EditPlan(String name,LocalDate startDate,String status) {}
    public record ChangeStatus(String status) {}
}

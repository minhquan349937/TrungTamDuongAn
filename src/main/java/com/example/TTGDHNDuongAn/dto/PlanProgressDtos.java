package com.example.TTGDHNDuongAn.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

public final class PlanProgressDtos {
    private PlanProgressDtos() { }
    public record EditPlan(String name, LocalDate startDate, String status) { }
    public record GoalStatus(String status) { }
    public record Observation(String notes, Integer successCount, Integer opportunityCount) { }
    public record Progress(String id, String planGoalId, LocalDateTime observedAt, String notes,
                           Integer successCount, Integer opportunityCount, String recordedBy) { }
}

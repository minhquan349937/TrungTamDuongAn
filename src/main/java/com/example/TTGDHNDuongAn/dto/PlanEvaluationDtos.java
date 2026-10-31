package com.example.TTGDHNDuongAn.dto;

import java.time.LocalDateTime;

public final class PlanEvaluationDtos {
    private PlanEvaluationDtos() {}
    public record Input(String evaluationNotes) {}
    public record Report(String id,String learningPlanId,String planName,String studentId,String studentName,
        String evaluationNotes,String evaluatedBy,String evaluatedByName,LocalDateTime evaluatedAt,
        boolean sentToParent,LocalDateTime sentAt,LocalDateTime updatedAt) {}
}

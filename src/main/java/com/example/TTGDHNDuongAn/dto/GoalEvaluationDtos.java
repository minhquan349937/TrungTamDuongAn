package com.example.TTGDHNDuongAn.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public final class GoalEvaluationDtos {
    private GoalEvaluationDtos() {}
    public record Input(BigDecimal achievementPercent,String restartDecision,String evaluationNotes) {}
    public record Evaluation(String id,String planGoalId,int attemptNo,BigDecimal achievementPercent,
        String resultStatus,String restartDecision,String evaluationNotes,LocalDateTime evaluatedAt,
        String evaluatedBy,String evaluatedByName,LocalDateTime createdAt,LocalDateTime updatedAt) {}
}

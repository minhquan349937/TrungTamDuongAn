package com.example.TTGDHNDuongAn.dto;

import tools.jackson.databind.JsonNode;
import java.math.BigDecimal;

public final class PlanGoalDtos {
    private PlanGoalDtos() {
    }

    public record AssignGoal(String goalId, String specificGoalId) {
    }

    public record AssignedGoal(String id, String planId, String goalId, String specificGoalId, String targetType, String targetId,
                               JsonNode goalSnapshot, JsonNode criteriaSnapshot, String status, BigDecimal achievementPercent) {
    }
}

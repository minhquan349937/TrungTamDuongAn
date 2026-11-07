package com.example.TTGDHNDuongAn.dto;

import tools.jackson.databind.JsonNode;
import java.math.BigDecimal;

public final class PlanGoalDtos {
    private PlanGoalDtos() {
    }

    public record AssignGoal(String goalId, String specificGoalId, java.util.List<GoalSelectionDtos.StrategyInput> strategies) {
        public AssignGoal(String goalId,String specificGoalId) { this(goalId,specificGoalId,null); }
    }

    public record AssignedGoal(String id, String planId, String goalId, String specificGoalId, String targetType, String targetId,
                               JsonNode goalSnapshot, JsonNode criteriaSnapshot, String status, BigDecimal achievementPercent,
                               java.util.List<GoalSelectionDtos.StrategySelection> strategies, boolean hasEvaluations) {
        public AssignedGoal(String id,String planId,String goalId,String specificGoalId,String targetType,String targetId,
                JsonNode goalSnapshot,JsonNode criteriaSnapshot,String status,BigDecimal achievementPercent) {
            this(id,planId,goalId,specificGoalId,targetType,targetId,goalSnapshot,criteriaSnapshot,status,achievementPercent,java.util.List.of(),achievementPercent!=null);
        }
    }
}

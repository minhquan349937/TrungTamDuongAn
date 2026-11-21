package com.example.TTGDHNDuongAn.dto;

import tools.jackson.databind.JsonNode;
import java.math.BigDecimal;

public final class PlanGoalDtos {
    private PlanGoalDtos() {
    }

    public record AssignGoal(String goalId, String specificGoalId, java.util.List<GoalSelectionDtos.StrategyInput> strategies,
                             java.util.List<GoalSelectionDtos.ActivityInput> activities) {
        public AssignGoal(String goalId,String specificGoalId) { this(goalId,specificGoalId,null,null); }
        public AssignGoal(String goalId,String specificGoalId,java.util.List<GoalSelectionDtos.StrategyInput> strategies) { this(goalId,specificGoalId,strategies,null); }
    }

    public record AssignedGoal(String id, String planId, String goalId, String specificGoalId, String targetType, String targetId,
                               JsonNode goalSnapshot, JsonNode criteriaSnapshot, String status, BigDecimal achievementPercent,
                               java.util.List<GoalSelectionDtos.StrategySelection> strategies,
                               java.util.List<GoalSelectionDtos.ActivitySelection> activities, boolean hasEvaluations) {
        public AssignedGoal(String id,String planId,String goalId,String specificGoalId,String targetType,String targetId,
                JsonNode goalSnapshot,JsonNode criteriaSnapshot,String status,BigDecimal achievementPercent) {
            this(id,planId,goalId,specificGoalId,targetType,targetId,goalSnapshot,criteriaSnapshot,status,achievementPercent,java.util.List.of(),java.util.List.of(),achievementPercent!=null);
        }
        public AssignedGoal(String id,String planId,String goalId,String specificGoalId,String targetType,String targetId,
                JsonNode goalSnapshot,JsonNode criteriaSnapshot,String status,BigDecimal achievementPercent,
                java.util.List<GoalSelectionDtos.StrategySelection> strategies,boolean hasEvaluations) {
            this(id,planId,goalId,specificGoalId,targetType,targetId,goalSnapshot,criteriaSnapshot,status,achievementPercent,strategies,java.util.List.of(),hasEvaluations);
        }
    }
}

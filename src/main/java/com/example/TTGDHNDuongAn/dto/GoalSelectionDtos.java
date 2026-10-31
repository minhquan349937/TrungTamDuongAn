package com.example.TTGDHNDuongAn.dto;

import java.util.List;

public final class GoalSelectionDtos {
    private GoalSelectionDtos() { }
    public record Target(String goalId, String specificGoalId) { }
    public record Selection(String goalId, String specificGoalId, String targetType,
                            String code, String name, String description,
                            GoalCatalogDtos.GoalLocation location, int ageMinMonths, int ageMaxMonths) {
        public String key() { return specificGoalId == null ? "G:"+goalId : "S:"+specificGoalId; }
    }
}

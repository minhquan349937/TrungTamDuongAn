package com.example.TTGDHNDuongAn.dto;

import java.util.List;

public final class GoalSelectionDtos {
    private GoalSelectionDtos() { }
    public record StrategyInput(String strategyId,String description) { }
    public record StrategySelection(String strategyId,String name,String description) { }
    public record Target(String goalId, String specificGoalId, List<StrategyInput> strategies) {
        public Target(String goalId,String specificGoalId) { this(goalId,specificGoalId,List.of()); }
    }
    public record Selection(String goalId, String specificGoalId, String targetType,
                            String code, String name, String description,
                            GoalCatalogDtos.GoalLocation location, int ageMinMonths, int ageMaxMonths,
                            List<StrategySelection> strategies) {
        public Selection(String goalId,String specificGoalId,String targetType,String code,String name,String description,
                GoalCatalogDtos.GoalLocation location,int ageMinMonths,int ageMaxMonths) {
            this(goalId,specificGoalId,targetType,code,name,description,location,ageMinMonths,ageMaxMonths,List.of());
        }
        public Selection withStrategies(List<StrategySelection> values) {
            return new Selection(goalId,specificGoalId,targetType,code,name,description,location,ageMinMonths,ageMaxMonths,values);
        }
        public String key() { return specificGoalId == null ? "G:"+goalId : "S:"+specificGoalId; }
    }
}

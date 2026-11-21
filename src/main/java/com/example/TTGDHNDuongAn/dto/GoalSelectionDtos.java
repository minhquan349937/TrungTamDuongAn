package com.example.TTGDHNDuongAn.dto;

import java.util.List;

public final class GoalSelectionDtos {
    private GoalSelectionDtos() { }
    public record StrategyInput(String strategyId,String description) { }
    public record StrategySelection(String strategyId,String name,String description) { }
    public record ActivityInput(String activityId,String note) { }
    public record ActivitySelection(String activityId,String name,String description,String note) { }
    public record Target(String goalId, String specificGoalId, List<StrategyInput> strategies, List<ActivityInput> activities) {
        public Target(String goalId,String specificGoalId) { this(goalId,specificGoalId,List.of(),List.of()); }
        public Target(String goalId,String specificGoalId,List<StrategyInput> strategies) { this(goalId,specificGoalId,strategies,List.of()); }
    }
    public record Selection(String goalId, String specificGoalId, String targetType,
                            String code, String name, String description,
                            GoalCatalogDtos.GoalLocation location, int ageMinMonths, int ageMaxMonths,
                            List<StrategySelection> strategies,List<ActivitySelection> activities) {
        public Selection(String goalId,String specificGoalId,String targetType,String code,String name,String description,
                GoalCatalogDtos.GoalLocation location,int ageMinMonths,int ageMaxMonths) {
            this(goalId,specificGoalId,targetType,code,name,description,location,ageMinMonths,ageMaxMonths,List.of(),List.of());
        }
        public Selection(String goalId,String specificGoalId,String targetType,String code,String name,String description,
                GoalCatalogDtos.GoalLocation location,int ageMinMonths,int ageMaxMonths,List<StrategySelection> strategies) {
            this(goalId,specificGoalId,targetType,code,name,description,location,ageMinMonths,ageMaxMonths,strategies,List.of());
        }
        public Selection withStrategies(List<StrategySelection> values) {
            return new Selection(goalId,specificGoalId,targetType,code,name,description,location,ageMinMonths,ageMaxMonths,values,activities);
        }
        public Selection withActivities(List<ActivitySelection> values) {
            return new Selection(goalId,specificGoalId,targetType,code,name,description,location,ageMinMonths,ageMaxMonths,strategies,values);
        }
        public String key() { return specificGoalId == null ? "G:"+goalId : "S:"+specificGoalId; }
    }
}

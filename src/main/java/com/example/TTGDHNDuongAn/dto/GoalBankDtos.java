package com.example.TTGDHNDuongAn.dto;

import java.util.List;

public final class GoalBankDtos {
    private GoalBankDtos() { }
    public record Target(String goalId, String specificGoalId) { }
    public record ReviewRequest(String programId, List<Target> selections) { }
    public record CompleteRequest(String programId, List<Target> selections, String reviewToken) { }
    public record Selection(String goalId, String specificGoalId, String targetType,
                            String code, String name, String description,
                            GoalCatalogDtos.GoalLocation location, int ageMinMonths, int ageMaxMonths) {
        public String key() { return specificGoalId == null ? "G:"+goalId : "S:"+specificGoalId; }
    }
    public record Review(String programId, List<Selection> items, int total, String reviewToken) { }
    public record BankEntry(String id, String studentId, Selection goalSnapshot, String selectedBy, String createdAt) { }
    public record Completion(int addedCount, int alreadyPresentCount, List<BankEntry> bank) { }
}

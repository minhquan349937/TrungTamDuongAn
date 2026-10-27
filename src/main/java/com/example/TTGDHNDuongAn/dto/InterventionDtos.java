package com.example.TTGDHNDuongAn.dto;

import java.time.LocalDate;
import java.util.List;

public final class InterventionDtos {
    private InterventionDtos() { }
    public record StudentSummary(String id, String code, String fullName, LocalDate birthDate,
            String status, int ageMonths, long studyingGoals, long achievedGoals) { }
    public record ProgramSummary(String id, String code, String name, String description,
            long domainCount, long goalCount, Integer ageMinMonths, Integer ageMaxMonths) { }
    public record AgeOption(String domainId, String levelId, String levelName, int ageMinMonths, int ageMaxMonths, String levelDomainId) { }
    public record DomainGroup(String id, String code, String name, List<AgeOption> ages) { }
    public record DomainRow(String code, String name, AgeOption age) { }
    public record GoalChoice(String id, String code, String name, String description, int displayOrder,
            boolean hasSpecificGoals, boolean canSelectGeneralGoal) { }
    public record DomainTargets(boolean supportsSpecificGoals, List<GoalChoice> goals) { }
    public record PlanView(StudentPlanDtos.Plan plan, List<PlanGoalDtos.AssignedGoal> goals) { }
    public record Workspace(StudentSummary student, List<PlanView> plans, List<GoalBankDtos.BankEntry> bank) { }
    public record Draft(String requestId, String programId, String name, LocalDate startDate,
            List<GoalBankDtos.Target> selections) { }
    public record Review(String requestId, String programId, String name, LocalDate startDate,
            List<GoalBankDtos.Selection> items, int total, String reviewToken) { }
    public record Complete(String requestId, String programId, String name, LocalDate startDate,
            List<GoalBankDtos.Target> selections, String reviewToken) { }
    public record Saved(PlanView result, boolean alreadySaved) { }
}

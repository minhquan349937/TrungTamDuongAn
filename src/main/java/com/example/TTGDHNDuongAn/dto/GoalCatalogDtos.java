package com.example.TTGDHNDuongAn.dto;

import java.util.List;

public final class GoalCatalogDtos {
    private GoalCatalogDtos() {
    }

    public record Program(String id, String code, String name, String description) {
    }

    public record ProgramLevel(String id, String programId, int levelNumber, String name,
                               int ageMinMonths, int ageMaxMonths, int displayOrder) {
    }

    public record Domain(String id, String programLevelId, String code, String name,
                         String description, int displayOrder, String levelDomainId) {
    }

    public record Goal(String id, String code, String name, String description, int displayOrder) {
    }

    public record SpecificGoal(String id, String code, String description, int ageMinMonths,
                               int ageMaxMonths, boolean inheritedAge, int displayOrder) {
    }

    public record GoalDetail(String id, String code, String name, String description,
                             List<SpecificGoal> specificGoals, boolean supportsSpecificGoals,
                             boolean canSelectGeneralGoal, GoalLocation location) {
    }

    public record GoalLocation(String programId, String programName, String levelId, String levelName,
                               String domainId, String domainName, int ageMinMonths, int ageMaxMonths) { }

    public record GoalHeader(String id, String code, String name, String description,
                             String programCode, GoalLocation location) { }

    public record Page<T>(List<T> content, int page, int size, long totalElements, long totalPages) {
    }

    public record CreateGoal(String code, String name, String description, Integer displayOrder) {
    }

    public record CreateProgram(String code, String name, String description) { }
    public record CreateLevel(Integer levelNumber, String name, Integer ageMinMonths,
                              Integer ageMaxMonths, Integer displayOrder) { }
    public record CreateDomain(String code, String name, Integer displayOrder) { }
    public record ProgramDomain(String id,String programId,String code,String name,String description) { }
    public record LinkDomain(String domainId,Integer displayOrder) { }
    public record WriteSpecificGoal(String code, String description, Integer ageMinMonths,
                                    Integer ageMaxMonths, Integer displayOrder) { }
}

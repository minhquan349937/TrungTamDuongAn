package com.example.TTGDHNDuongAn.service;
import com.example.TTGDHNDuongAn.dto.GoalCatalogDtos;
import com.example.TTGDHNDuongAn.model.catalog.CatalogGoal;
public final class CatalogGoalMapper {
    private CatalogGoalMapper() { }
    public static GoalCatalogDtos.GoalHeader header(CatalogGoal goal) {
        var link=goal.getLevelDomain(); var level=link.getLevel(); var domain=link.getDomain(); var program=link.getProgram();
        return new GoalCatalogDtos.GoalHeader(goal.getId(),goal.getCode(),goal.getName(),goal.getDescription(),program.getCode(),
            new GoalCatalogDtos.GoalLocation(program.getId(),program.getName(),level.getId(),level.getName(),domain.getId(),domain.getName(),level.getAgeMinMonths(),level.getAgeMaxMonths()));
    }
}

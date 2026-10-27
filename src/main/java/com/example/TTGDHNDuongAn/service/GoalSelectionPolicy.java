package com.example.TTGDHNDuongAn.service;

import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Component;

/** Program rules live in the backend; the UI consumes capabilities, never program names. */
@Component
public class GoalSelectionPolicy {
    private static final Set<String> MAIN_GOAL_ONLY = Set.of("ESDM", "DENVER");

    public boolean supportsSpecificGoals(String programCode) {
        return !MAIN_GOAL_ONLY.contains(programCode.toUpperCase(Locale.ROOT));
    }

    public boolean canSelectGeneralGoal(String programCode, boolean hasSpecificGoals) {
        return !"VB".equalsIgnoreCase(programCode) || !hasSpecificGoals;
    }
}

package com.example.TTGDHNDuongAn.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

public final class StudentPlanDtos {
    private StudentPlanDtos() {
    }

    public record Student(String id, String code, String fullName, LocalDate birthDate,
                          LocalDate admittedOn, String status) {
    }

    public record CreateStudent(String code, String fullName, LocalDate birthDate, LocalDate admittedOn) {
    }

    public record Plan(String id, String studentId, String name, LocalDate startDate, String status,
        LocalDate endDate,LocalDateTime createdAt,boolean hasEvaluation,boolean sentToParent) {
    }

    public record CreatePlan(String name, LocalDate startDate) {
    }
}

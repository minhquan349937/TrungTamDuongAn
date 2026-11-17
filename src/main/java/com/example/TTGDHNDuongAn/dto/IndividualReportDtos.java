package com.example.TTGDHNDuongAn.dto;

import com.example.TTGDHNDuongAn.service.ReportSectionParser.Sections;
import java.math.BigDecimal;
import java.time.*;
import java.util.List;

public final class IndividualReportDtos {
    private IndividualReportDtos() {}
    public record Student(String id,String code,String fullName,long reportCount) {}
    public record Summary(String id,String planId,String planName,LocalDate planStartDate,LocalDate planEndDate,
        LocalDateTime evaluatedAt,String evaluatedByName,boolean editable) {}
    public record Goal(String id,String domain,String code,String name,BigDecimal achievementPercent,String status,String notes) {}
    public record Detail(PlanEvaluationDtos.Report report,String studentCode,LocalDate planStartDate,LocalDate planEndDate,
        LocalDateTime resultsAsOf,boolean editable,Sections sections,List<Goal> goals,List<String> warnings) {}
    public record Edit(String strengths,String difficulties,String supportDirections,LocalDateTime expectedUpdatedAt) {}
}

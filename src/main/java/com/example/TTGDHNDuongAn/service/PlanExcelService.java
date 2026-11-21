package com.example.TTGDHNDuongAn.service;

import com.example.TTGDHNDuongAn.dto.PlanGoalDtos.AssignedGoal;
import com.example.TTGDHNDuongAn.repository.PlanGoalRepository;
import com.example.TTGDHNDuongAn.repository.StudentPlanRepository;
import com.example.TTGDHNDuongAn.security.AppUserPrincipal;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PlanExcelService {
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private final JdbcTemplate jdbc;
    private final StudentPlanRepository plans;
    private final PlanGoalRepository goals;
    private final PlanAccessService access;

    public PlanExcelService(JdbcTemplate jdbc, StudentPlanRepository plans, PlanGoalRepository goals, PlanAccessService access) {
        this.jdbc = jdbc;
        this.plans = plans;
        this.goals = goals;
        this.access = access;
    }

    @Transactional(readOnly = true)
    public byte[] export(String planId, AppUserPrincipal actor) {
        String studentId = jdbc.query("SELECT student_id FROM learning_plans WHERE id=?",
            rs -> rs.next() ? rs.getString(1) : null, planId);
        if (studentId == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy kế hoạch.");
        access.requireStudent(studentId, actor);
        var student = plans.student(studentId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy học sinh."));
        var plan = plans.plans(studentId).stream().filter(row -> row.id().equals(planId)).findFirst()
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy kế hoạch."));
        try (var workbook = new XSSFWorkbook(); var output = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("Kế hoạch cá nhân");
            int[] widths = {8, 28, 55, 38, 38, 40};
            for (int i = 0; i < widths.length; i++) sheet.setColumnWidth(i, widths[i] * 256);
            var titleStyle = style(workbook, true, false, 15, HorizontalAlignment.CENTER);
            var labelStyle = style(workbook, true, false, 11, HorizontalAlignment.LEFT);
            var valueStyle = style(workbook, false, false, 11, HorizontalAlignment.LEFT);
            var headerStyle = style(workbook, true, true, 11, HorizontalAlignment.CENTER);
            var bodyStyle = style(workbook, false, true, 11, HorizontalAlignment.LEFT);
            var numberStyle = style(workbook, false, true, 11, HorizontalAlignment.CENTER);

            var title = sheet.createRow(0);
            title.setHeightInPoints(30);
            cell(title, 0, "KẾ HOẠCH CÁ NHÂN", titleStyle);
            sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 5));
            metadata(sheet, 1, "Tên học sinh", student.fullName(), "Tên kế hoạch", plan.name(), labelStyle, valueStyle);
            metadata(sheet, 2, "Ngày bắt đầu", date(plan.startDate()), "Ngày kết thúc", date(plan.endDate()), labelStyle, valueStyle);
            metadata(sheet, 3, "Trạng thái", status(plan.status()), null, null, labelStyle, valueStyle);

            var header = sheet.createRow(5);
            header.setHeightInPoints(27);
            String[] headings = {"STT", "Lĩnh vực", "Mục tiêu", "Hoạt động gợi ý", "Chiến lược", "Ghi chú"};
            for (int i = 0; i < headings.length; i++) cell(header, i, headings[i], headerStyle);
            List<AssignedGoal> assigned = goals.list(planId);
            int totalRows = Math.max(assigned.size(), 8);
            for (int i = 0; i < totalRows; i++) {
                var row = sheet.createRow(6 + i);
                row.setHeightInPoints(28);
                if (i < assigned.size()) {
                    var goal = assigned.get(i);
                    var snapshot = goal.goalSnapshot();
                    String code = snapshot.path("code").asText("");
                    String name = snapshot.path("name").asText("");
                    String description = snapshot.path("description").asText("");
                    cell(row, 0, String.valueOf(i + 1), numberStyle);
                    cell(row, 1, snapshot.path("domain_name").asText(""), bodyStyle);
                    cell(row, 2, joinNonEmpty(code.isBlank() ? name : code + " · " + name,
                        description.equals(name) ? "" : description), bodyStyle);
                    cell(row, 3, goal.activities().stream().map(a -> a.name()).filter(s -> s != null && !s.isBlank()).reduce((a,b) -> a + "\n" + b).orElse(""), bodyStyle);
                    cell(row, 4, goal.strategies().stream().map(s -> s.name()).filter(s -> s != null && !s.isBlank()).reduce((a,b) -> a + "\n" + b).orElse(""), bodyStyle);
                    var notes = new ArrayList<String>();
                    goal.activities().forEach(a -> { if (a.note() != null && !a.note().isBlank()) notes.add(a.name() + ": " + a.note()); });
                    goal.strategies().forEach(s -> { if (s.description() != null && !s.description().isBlank()) notes.add(s.name() + ": " + s.description()); });
                    cell(row, 5, String.join("\n", notes), bodyStyle);
                    int lines = Math.max(2, Math.max(estimatedLines(row.getCell(2).getStringCellValue(), 45),
                        Math.max(estimatedLines(row.getCell(3).getStringCellValue(), 32),
                            Math.max(estimatedLines(row.getCell(4).getStringCellValue(), 32),
                                estimatedLines(row.getCell(5).getStringCellValue(), 34)))));
                    row.setHeightInPoints(Math.min(400, lines * 17 + 8));
                } else {
                    for (int column = 0; column < 6; column++) cell(row, column, "", bodyStyle);
                }
            }
            sheet.setAutobreaks(true);
            sheet.setRepeatingRows(new CellRangeAddress(5, 5, 0, 5));
            sheet.setFitToPage(true);
            var print = sheet.getPrintSetup();
            print.setLandscape(true);
            print.setPaperSize(PrintSetup.A4_PAPERSIZE);
            print.setFitWidth((short) 1);
            print.setFitHeight((short) 0);
            sheet.setMargin(Sheet.LeftMargin, 0.3);
            sheet.setMargin(Sheet.RightMargin, 0.3);
            workbook.write(output);
            return output.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException("Không thể tạo tệp Excel.", ex);
        }
    }

    private static void metadata(Sheet sheet, int rowIndex, String firstLabel, String firstValue,
            String secondLabel, String secondValue, CellStyle labelStyle, CellStyle valueStyle) {
        var row = sheet.createRow(rowIndex);
        row.setHeightInPoints(23);
        cell(row, 0, firstLabel, labelStyle);
        cell(row, 1, firstValue, valueStyle);
        sheet.addMergedRegion(new CellRangeAddress(rowIndex, rowIndex, 1, 2));
        if (secondLabel != null) {
            cell(row, 3, secondLabel, labelStyle);
            cell(row, 4, secondValue, valueStyle);
            sheet.addMergedRegion(new CellRangeAddress(rowIndex, rowIndex, 4, 5));
        }
    }

    private static CellStyle style(Workbook workbook, boolean bold, boolean border, int fontSize, HorizontalAlignment align) {
        var style = workbook.createCellStyle();
        var font = workbook.createFont();
        font.setBold(bold);
        font.setFontHeightInPoints((short) fontSize);
        style.setFont(font);
        style.setAlignment(align);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setWrapText(true);
        if (border) {
            style.setBorderTop(BorderStyle.THIN);
            style.setBorderBottom(BorderStyle.THIN);
            style.setBorderLeft(BorderStyle.THIN);
            style.setBorderRight(BorderStyle.THIN);
        }
        return style;
    }

    private static void cell(Row row, int column, String value, CellStyle style) {
        var cell = row.createCell(column);
        cell.setCellValue(value == null ? "" : value);
        cell.setCellStyle(style);
    }

    private static String date(LocalDate value) { return value == null ? "Chưa có" : DATE.format(value); }
    private static String joinNonEmpty(String first, String second) { return second == null || second.isBlank() ? first : first + "\n" + second; }
    private static int estimatedLines(String value, int charsPerLine) {
        int lines = 0;
        for (String part : value.split("\n", -1)) lines += Math.max(1, (part.length() + charsPerLine - 1) / charsPerLine);
        return lines;
    }
    private static String status(String value) {
        return switch (value) {
            case "DRAFT" -> "Bản nháp";
            case "ACTIVE" -> "Đang học";
            case "COMPLETED" -> "Hoàn thành";
            case "PAUSED" -> "Tạm dừng";
            case "CANCELLED" -> "Đã hủy";
            default -> value;
        };
    }
}

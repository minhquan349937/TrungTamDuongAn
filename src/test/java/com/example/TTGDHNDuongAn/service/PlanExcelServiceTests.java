package com.example.TTGDHNDuongAn.service;

import static org.junit.jupiter.api.Assertions.*;
import com.example.TTGDHNDuongAn.security.AppUserPrincipal;
import java.io.ByteArrayInputStream;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(properties={"SETUP_ADMIN_EMAIL=","SETUP_ADMIN_PASSWORD=","spring.docker.compose.enabled=false","debug=false"})
class PlanExcelServiceTests {
    @Autowired PlanExcelService service;
    @Autowired JdbcTemplate jdbc;

    @Test void exportsRequestedPlanWithStudentAndExpectedColumns() throws Exception {
        var ids = jdbc.queryForList("SELECT id FROM learning_plans ORDER BY id LIMIT 1", String.class);
        Assumptions.assumeTrue(!ids.isEmpty(), "Requires an existing plan");
        var studentName = jdbc.queryForObject("SELECT s.full_name FROM student s JOIN learning_plans p ON p.student_id=s.id WHERE p.id=?",
            String.class, ids.getFirst());
        byte[] bytes = service.export(ids.getFirst(), new AppUserPrincipal("admin", null, "Admin", "ADMIN", ""));
        try (var workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            var sheet = workbook.getSheetAt(0);
            assertEquals("KẾ HOẠCH CÁ NHÂN", sheet.getRow(0).getCell(0).getStringCellValue());
            assertEquals(studentName, sheet.getRow(1).getCell(1).getStringCellValue());
            assertEquals("Ngày bắt đầu", sheet.getRow(2).getCell(0).getStringCellValue());
            assertEquals("Ngày kết thúc", sheet.getRow(2).getCell(3).getStringCellValue());
            assertEquals("Trạng thái", sheet.getRow(3).getCell(0).getStringCellValue());
            assertEquals("STT", sheet.getRow(5).getCell(0).getStringCellValue());
            assertEquals("Lĩnh vực", sheet.getRow(5).getCell(1).getStringCellValue());
            assertEquals("Mục tiêu", sheet.getRow(5).getCell(2).getStringCellValue());
            assertEquals("Hoạt động gợi ý", sheet.getRow(5).getCell(3).getStringCellValue());
            assertEquals("Chiến lược", sheet.getRow(5).getCell(4).getStringCellValue());
            assertEquals("Ghi chú", sheet.getRow(5).getCell(5).getStringCellValue());
        }
    }
}

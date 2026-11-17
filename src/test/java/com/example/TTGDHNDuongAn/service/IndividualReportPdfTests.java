package com.example.TTGDHNDuongAn.service;

import static org.junit.jupiter.api.Assertions.*;
import com.example.TTGDHNDuongAn.dto.IndividualReportDtos.*;
import com.example.TTGDHNDuongAn.dto.PlanEvaluationDtos.Report;
import java.math.BigDecimal;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.rendering.PDFRenderer;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class IndividualReportPdfTests {
    final IndividualReportPdfService pdf=new IndividualReportPdfService();
    Detail detail(String notes,List<Goal> goals) {
        var time=LocalDateTime.of(2026,10,30,8,0);
        return new Detail(new Report("NX000001","KH000001","Kế hoạch tháng mười","HS000001","Học sinh giả lập Nguyễn Ánh",
            notes,"TK000001","Giáo viên Đường An",time,false,null,time),"HS01",LocalDate.of(2026,10,1),LocalDate.of(2026,10,31),time,true,
            new ReportSectionParser().parse(notes),goals,List.of());
    }
    @Test void vietnameseMultiPageTablesSectionsAndSignaturesAreReadable() throws Exception {
        var goals=new ArrayList<Goal>();
        for(int i=0;i<65;i++) goals.add(new Goal("goal-"+i,i<30?"Giao tiếp":"Vận động","D."+i,
            "Mục tiêu giả lập "+i+": Học sinh thực hiện hoạt động phù hợp với khả năng và nhu cầu hỗ trợ.",new BigDecimal("80"),"ACHIEVED","Ghi nhận thực tế của giáo viên."));
        String notes=new ReportSectionParser().compose("Tiến bộ được giáo viên ghi nhận. ".repeat(150),"Cần hỗ trợ tập trung.","Đề xuất luyện tập, không chẩn đoán.");
        byte[] bytes=pdf.render(detail(notes,goals));
        try(var doc=Loader.loadPDF(bytes)) {
            assertTrue(doc.getNumberOfPages()>3);
            for(var page:doc.getPages()) {assertEquals(PDRectangle.A4.getWidth(),page.getMediaBox().getWidth());assertTrue(page.getMediaBox().getHeight()>page.getMediaBox().getWidth());}
            String text=new PDFTextStripper().getText(doc);
            assertTrue(text.contains("BÁO CÁO ĐÁNH GIÁ CÁ NHÂN"));assertTrue(text.contains("Nguyễn Ánh"));
            assertTrue(text.contains("II. Khó khăn cần hỗ trợ"));assertTrue(text.contains("III. Định hướng hỗ trợ"));
            assertTrue(text.contains("D.64"));assertTrue(text.contains("ĐỀ") || text.contains("Đề xuất luyện tập"));
            assertFalse(text.contains("GEMINI_API_KEY"));
            var stripper=new PDFTextStripper();int signaturePage=0;
            for(int i=1;i<=doc.getNumberOfPages();i++) {
                stripper.setStartPage(i);stripper.setEndPage(i);String pageText=stripper.getText(doc);
                assertTrue(pageText.contains("Trang "+i+" / "+doc.getNumberOfPages()));
                if(pageText.contains("GIÁO VIÊN ĐÁNH GIÁ")) {signaturePage=i;assertTrue(pageText.contains("ĐẠI DIỆN TRUNG TÂM"));assertTrue(pageText.contains("Ký và ghi rõ họ tên"));}
            }
            assertEquals(doc.getNumberOfPages(),signaturePage);
            // Synthetic-only review artifacts, ignored by Git with the rest of target/.
            Path output=Path.of("target/report-pdf-preview");Files.createDirectories(output);
            Files.write(output.resolve("synthetic-individual-report.pdf"),bytes);
            var renderer=new PDFRenderer(doc);
            ImageIO.write(renderer.renderImageWithDPI(0,100),"png",output.resolve("first-page.png").toFile());
            ImageIO.write(renderer.renderImageWithDPI(doc.getNumberOfPages()-1,100),"png",output.resolve("last-page.png").toFile());
        }
    }
    @Test void legacyTextIsExportedAsSavedAndEmptyGoalsAreSupported() throws Exception {
        String original="Văn bản cũ chưa có tiêu đề.\nGiữ nguyên nhận xét thực tế của giáo viên.";
        try(var doc=Loader.loadPDF(pdf.render(detail(original,List.of())))) {
            String text=new PDFTextStripper().getText(doc);
            assertTrue(text.contains("NHẬN XÉT ĐÃ LƯU"));assertTrue(text.contains("Giữ nguyên nhận xét thực tế"));
            assertFalse(text.contains("I. Tiến bộ"));assertTrue(text.contains("Chưa có mục tiêu"));
        }
    }
    @Test void oversizedGoalRowsContinueWithoutLosingTextOrBreakingRendering() throws Exception {
        var goal=new Goal("long","Lĩnh vực giả lập","VB1","Nội dung dài ".repeat(1500)+"KẾT THÚC MỤC TIÊU",null,"HISTORICAL_UNAVAILABLE","Không đủ dữ liệu lịch sử");
        try(var doc=Loader.loadPDF(pdf.render(detail("Nhận xét nguyên bản",List.of(goal))))) {
            String text=new PDFTextStripper().getText(doc);
            assertTrue(doc.getNumberOfPages()>2);assertTrue(text.contains("KẾT THÚC MỤC TIÊU"));assertTrue(text.contains("Lĩnh vực giả lập (tiếp)"));
        }
    }
}

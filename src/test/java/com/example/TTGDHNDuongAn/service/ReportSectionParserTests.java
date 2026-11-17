package com.example.TTGDHNDuongAn.service;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ReportSectionParserTests {
    final ReportSectionParser parser=new ReportSectionParser();
    @ParameterizedTest
    @ValueSource(strings={
        "I. Tiến bộ\nChủ động giao tiếp.\nII. Khó khăn cần hỗ trợ\nCần nhắc tập trung.\nIII. Định hướng hỗ trợ\nLuyện tập phù hợp.",
        "1. **Tiến bộ:** Chủ động giao tiếp.\n2. **Khó khăn cần hỗ trợ:** Cần nhắc tập trung.\n3. **Định hướng hỗ trợ:** Luyện tập phù hợp.",
        "**I. Tiến bộ**: Chủ động giao tiếp.\n**II. Khó khăn cần hỗ trợ**: Cần nhắc tập trung.\n**III. Định hướng hỗ trợ**: Luyện tập phù hợp.",
        "### Tiến bộ:\r\nChủ động giao tiếp.\r\n### Khó khăn cần hỗ trợ:\r\nCần nhắc tập trung.\r\n### Định hướng hỗ trợ:\r\nLuyện tập phù hợp.",
        "  **ĐIỂM MẠNH**  :  Chủ động giao tiếp.\n\n**Hạn chế**: Cần nhắc tập trung.\n\n**Phương hướng hỗ trợ**: Luyện tập phù hợp."
    })
    void parsesCommonNumberingMarkdownWhitespaceAndAliases(String original) {
        var sections=parser.parse(original);
        assertTrue(sections.structured());assertEquals(original,sections.originalText());
        assertEquals("Chủ động giao tiếp.",sections.strengths());
        assertEquals("Cần nhắc tập trung.",sections.difficulties());
        assertEquals("Luyện tập phù hợp.",sections.supportDirections());
    }
    @ParameterizedTest @ValueSource(strings={"","Nhận xét cũ không có tiêu đề.","Tiến bộ\nA\nKhó khăn\nB", "Khó khăn\nB\nTiến bộ\nA\nĐịnh hướng\nC",
        "Lời dẫn cần giữ\nI. Tiến bộ\nA\nII. Khó khăn\nB\nIII. Định hướng\nC", "Tiến bộ\nA\nTiến bộ\nB\nKhó khăn\nC\nĐịnh hướng\nD"})
    void ambiguousOrLegacyReportsRemainUnchanged(String original) {
        var sections=parser.parse(original);assertFalse(sections.structured());assertEquals(original,sections.originalText());
        assertEquals("",sections.strengths());assertEquals("",sections.difficulties());assertEquals("",sections.supportDirections());
    }
    @Test void nullAndEmptySectionsDoNotInventContent() {
        assertEquals("",parser.parse(null).originalText());
        var sections=parser.parse("I. Tiến bộ\n\nII. Khó khăn cần hỗ trợ\n\nIII. Định hướng hỗ trợ\n");
        assertTrue(sections.structured());assertEquals("",sections.strengths());
        assertThrows(IllegalArgumentException.class,()->parser.compose(null,"B","C"));
        assertThrows(IllegalArgumentException.class,()->parser.compose(" ","","\n"));
    }
    @Test void compositionKeepsEveryTextSectionAndInternalLineBreak() {
        String first="Dòng đầu\n\nDòng tiếp theo: có dấu tiếng Việt.";
        var sections=parser.parse(parser.compose(first,"Khó khăn thực tế","Đề xuất của giáo viên"));
        assertTrue(sections.structured());assertEquals(first,sections.strengths());
        assertEquals("Khó khăn thực tế",sections.difficulties());assertEquals("Đề xuất của giáo viên",sections.supportDirections());
        var exactTitles=parser.parse(parser.compose("Tiến bộ","Khó khăn","Định hướng"));
        assertTrue(exactTitles.structured());assertEquals("Khó khăn",exactTitles.difficulties());
    }
}

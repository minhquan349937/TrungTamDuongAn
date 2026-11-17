package com.example.TTGDHNDuongAn.service;

import java.util.*;
import java.util.regex.*;
import org.springframework.stereotype.Component;

@Component
public class ReportSectionParser {
    public record Sections(boolean structured, String strengths, String difficulties, String supportDirections, String originalText) {}
    private static final Pattern HEADING=Pattern.compile(
        "^\\s*(?:#{1,6}\\s*)?(?:\\*\\*|__)?\\s*(?:(?:III|II|I|[123])\\s*[.)]\\s*)?(?:\\*\\*|__)?\\s*"+
        "(Tiến bộ|Những tiến bộ|Điểm mạnh|Ưu điểm|Khó khăn cần hỗ trợ|Khó khăn|Những khó khăn|Hạn chế|"+
        "Định hướng hỗ trợ|Định hướng|Hướng hỗ trợ|Phương hướng hỗ trợ|Đề xuất hỗ trợ)"+
        "\\s*(?:\\*\\*|__)?\\s*(?::\\s*(?:\\*\\*|__)?\\s*(.*)|$)",Pattern.CASE_INSENSITIVE|Pattern.UNICODE_CASE);
    private static final Pattern NUMBERED=Pattern.compile("^\\s*(?:#{1,6}\\s*)?(?:\\*\\*|__)?\\s*(?:III|II|I|[123])\\s*[.)]",Pattern.CASE_INSENSITIVE);

    public Sections parse(String text) {
        String original=text==null?"":text;
        var parts=new ArrayList<StringBuilder>();
        int current=-1;
        var lines=original.replace("\r\n","\n").replace('\r','\n').split("\n",-1);
        // With three explicit numbered headings, matching words inside a section are body text.
        boolean numbered=Arrays.stream(lines).filter(line->HEADING.matcher(line).matches() && NUMBERED.matcher(line).find()).count()==3;
        for(String line:lines) {
            Matcher matcher=HEADING.matcher(line);
            if(matcher.matches() && (!numbered || NUMBERED.matcher(line).find())) {
                String title=matcher.group(1).toLowerCase(Locale.ROOT);
                int section=title.contains("tiến bộ") || Set.of("điểm mạnh","ưu điểm").contains(title)?0:
                    title.contains("khó khăn") || title.equals("hạn chế")?1:2;
                if(section!=current+1) return fallback(original);
                current=section;parts.add(new StringBuilder());
                if(matcher.group(2)!=null) parts.get(current).append(matcher.group(2));
            } else {
                if(current<0) { if(!line.isBlank()) return fallback(original); }
                else { if(!parts.get(current).isEmpty()) parts.get(current).append('\n');parts.get(current).append(line); }
            }
        }
        if(parts.size()!=3) return fallback(original);
        return new Sections(true,parts.get(0).toString().strip(),parts.get(1).toString().strip(),parts.get(2).toString().strip(),original);
    }
    private Sections fallback(String original) { return new Sections(false,"","","",original); }
    public String compose(String strengths,String difficulties,String directions) {
        if(strengths==null || difficulties==null || directions==null)
            throw new IllegalArgumentException("Cần cung cấp đủ ba phần nhận xét.");
        if(strengths.isBlank() && difficulties.isBlank() && directions.isBlank())
            throw new IllegalArgumentException("Vui lòng nhập nội dung nhận xét.");
        return "I. Tiến bộ\n"+strengths+"\n\nII. Khó khăn cần hỗ trợ\n"+difficulties+"\n\nIII. Định hướng hỗ trợ\n"+directions;
    }
}

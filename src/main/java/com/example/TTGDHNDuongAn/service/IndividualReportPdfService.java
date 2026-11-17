package com.example.TTGDHNDuongAn.service;

import com.example.TTGDHNDuongAn.dto.IndividualReportDtos.*;
import java.awt.Color;
import java.io.*;
import java.text.Normalizer;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.*;
import org.springframework.stereotype.Service;

@Service
public class IndividualReportPdfService {
    private static final DateTimeFormatter DATE=DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final Map<String,String> RESULTS=Map.of("ACHIEVED","Đạt","NOT_ACHIEVED","Không đạt",
        "NOT_STARTED","Chưa thực hiện","HISTORICAL_UNAVAILABLE","Không đủ dữ liệu lịch sử");

    public byte[] render(Detail detail) throws IOException {
        try(var doc=new PDDocument();var bytes=new ByteArrayOutputStream()) {
            doc.getDocumentInformation().setTitle("Báo cáo đánh giá cá nhân");
            doc.getDocumentInformation().setAuthor("Trung tâm Đường An");
            try(var regular=getClass().getResourceAsStream("/report-fonts/NotoSans-Regular.ttf");
                var bold=getClass().getResourceAsStream("/report-fonts/NotoSans-Bold.ttf")) {
                if(regular==null || bold==null) throw new IOException("Missing report font");
                var layout=new Layout(doc,PDType0Font.load(doc,regular),PDType0Font.load(doc,bold));
                layout.start();
                layout.title("BÁO CÁO ĐÁNH GIÁ CÁ NHÂN");
                layout.paragraph("Học sinh: "+detail.report().studentName()+"   |   Mã: "+detail.studentCode());
                layout.paragraph("Kế hoạch: "+detail.report().planName());
                layout.paragraph("Kỳ kế hoạch: "+DATE.format(detail.planStartDate())+" – "+(detail.planEndDate()==null?"Chưa có ngày kết thúc":DATE.format(detail.planEndDate())));
                layout.paragraph("Giáo viên đánh giá: "+detail.report().evaluatedByName());
                layout.paragraph("Ngày đánh giá: "+DATE.format(detail.report().evaluatedAt().plusHours(7))+
                    "   |   Kết quả tính đến: "+DATE.format(detail.resultsAsOf().plusHours(7)));
                for(String warning:detail.warnings()) layout.paragraph("Lưu ý: "+warning);
                layout.heading("KẾT QUẢ THỰC HIỆN MỤC TIÊU");
                var domains=new LinkedHashMap<String,List<Goal>>();
                for(Goal goal:detail.goals()) domains.computeIfAbsent(goal.domain(),key->new ArrayList<>()).add(goal);
                if(domains.isEmpty()) layout.paragraph("Chưa có mục tiêu trong kỳ báo cáo.");
                for(var domain:domains.entrySet()) layout.table(domain.getKey(),domain.getValue());
                var sections=detail.sections();
                if(sections.structured()) {
                    layout.heading("I. Tiến bộ");layout.paragraph(sections.strengths());
                    layout.heading("II. Khó khăn cần hỗ trợ");layout.paragraph(sections.difficulties());
                    layout.heading("III. Định hướng hỗ trợ");layout.paragraph(sections.supportDirections());
                } else {
                    layout.heading("NHẬN XÉT ĐÃ LƯU");layout.paragraph(sections.originalText());
                }
                layout.signatures(DATE.format(detail.report().evaluatedAt().plusHours(7)));
                layout.finish();
                doc.save(bytes);
                return bytes.toByteArray();
            }
        }
    }

    /** Page layout is local to one request. Row borders and signature blocks never cross a page boundary. */
    private static final class Layout {
        private static final float LEFT=44,WIDTH=507,BOTTOM=52,TOP=790,LINE=15;
        private static final Color GREEN=new Color(35,92,76),GRAY=new Color(226,233,229);
        private final PDDocument doc;
        private final PDFont regular,bold;
        private PDPageContentStream out;
        private float y;
        Layout(PDDocument doc,PDFont regular,PDFont bold) {this.doc=doc;this.regular=regular;this.bold=bold;}
        void start() throws IOException { page(); }
        void page() throws IOException {
            if(out!=null) out.close();
            var page=new PDPage(PDRectangle.A4);doc.addPage(page);out=new PDPageContentStream(doc,page);y=TOP;
            // Sprout mark used by the application's existing branding, drawn as PDF vectors.
            out.setStrokingColor(GREEN);out.setLineWidth(2);
            out.moveTo(LEFT+12,y-16);out.lineTo(LEFT+12,y+2);out.stroke();
            out.moveTo(LEFT+12,y-3);out.curveTo(LEFT-2,y-3,LEFT-2,y+13,LEFT+12,y+4);
            out.curveTo(LEFT+20,y+17,LEFT+31,y+10,LEFT+12,y-3);out.stroke();
            text("TRUNG TÂM ĐƯỜNG AN",LEFT+40,y,14,bold,GREEN);
            text("Đồng hành cùng sự phát triển của học sinh",LEFT+40,y-18,9,regular,GREEN);
            y-=40;out.moveTo(LEFT,y);out.lineTo(LEFT+WIDTH,y);out.stroke();y-=26;
        }
        void ensure(float height) throws IOException { if(y-height<BOTTOM) page(); }
        void text(String value,float x,float baseline,float size,PDFont font,Color color) throws IOException {
            out.beginText();out.setFont(font,size);out.setNonStrokingColor(color);out.newLineAtOffset(x,baseline);out.showText(safe(value,font));out.endText();
        }
        String safe(String value,PDFont font) {
            String normalized=Normalizer.normalize(value==null?"":value,Normalizer.Form.NFC);
            var result=new StringBuilder();
            normalized.codePoints().forEach(cp->{
                if(Character.isISOControl(cp) || cp==0x2028 || cp==0x2029) {result.append(' ');return;}
                String glyph=new String(Character.toChars(cp));
                try {font.encode(glyph);result.append(glyph);} catch(IOException|IllegalArgumentException ex) {result.append('?');}
            });return result.toString();
        }
        List<String> wrap(String value,float width,float size,PDFont font) throws IOException {
            var lines=new ArrayList<String>();
            for(String paragraph:(value==null?"":value).replace("\r\n","\n").replace('\r','\n').split("\n",-1)) {
                String normalized=safe(paragraph,font);
                if(normalized.isEmpty()) {lines.add("");continue;}
                StringBuilder line=new StringBuilder();
                for(String word:normalized.split("\\s+")) {
                    String candidate=line.isEmpty()?word:line+" "+word;
                    if(font.getStringWidth(candidate)/1000*size<=width) {line.setLength(0);line.append(candidate);continue;}
                    if(!line.isEmpty()) {lines.add(line.toString());line.setLength(0);}
                    // Also wrap long unbroken strings, preserving every printable character.
                    for(int cp:word.codePoints().toArray()) {
                        String glyph=new String(Character.toChars(cp));
                        if(!line.isEmpty() && font.getStringWidth(line+glyph)/1000*size>width) {lines.add(line.toString());line.setLength(0);}
                        line.append(glyph);
                    }
                }
                if(!line.isEmpty()) lines.add(line.toString());
            }return lines;
        }
        void title(String value) throws IOException {
            for(String line:wrap(value,WIDTH,16,bold)) {ensure(25);text(line,LEFT+(WIDTH-bold.getStringWidth(line)/1000*16)/2,y,16,bold,GREEN);y-=25;}y-=8;
        }
        void heading(String value) throws IOException {
            var lines=wrap(value,WIDTH,11,bold);ensure(lines.size()*LINE+35);y-=10;
            for(String line:lines) {text(line,LEFT,y,11,bold,GREEN);y-=LINE;}y-=5;
        }
        void paragraph(String value) throws IOException {
            for(String line:wrap(value,WIDTH,10,regular)) {ensure(LINE);text(line,LEFT,y,10,regular,Color.BLACK);y-=LINE;}y-=6;
        }
        void tableHeader(String domain) throws IOException {
            var labels=wrap(domain,WIDTH,10,bold);ensure(labels.size()*LINE+66);
            for(String label:labels) {text(label,LEFT,y,10,bold,GREEN);y-=LINE;}
            row(List.of("Mục tiêu","Kết quả","Ghi nhận"),true); // repeated on each continuation page
        }
        void table(String domain,List<Goal> goals) throws IOException {
            tableHeader(domain);
            for(Goal goal:goals) {
                String result=RESULTS.getOrDefault(goal.status(),"Chưa xác định");
                if(goal.achievementPercent()!=null) result+=" ("+goal.achievementPercent().stripTrailingZeros().toPlainString()+"%)";
                var cells=List.of(goal.code()+" · "+goal.name(),result,goal.notes()==null?"":goal.notes());
                var wrapped=new ArrayList<List<String>>();
                float[] widths={245,100,162};
                for(int i=0;i<3;i++) wrapped.add(wrap(cells.get(i),widths[i]-12,9,regular));
                int count=wrapped.stream().mapToInt(List::size).max().orElse(1);
                int offset=0;
                while(offset<count) {
                    // A very long goal is continued in explicitly marked rows; each row stays intact.
                    int chunk=Math.min(34,count-offset);
                    float height=chunk*13+12;
                    if(y-height<BOTTOM) {page();tableHeader(domain+" (tiếp)");}
                    var segment=new ArrayList<List<String>>();
                    for(var cell:wrapped) segment.add(offset>=cell.size()?List.of():cell.subList(offset,Math.min(offset+chunk,cell.size())));
                    drawRow(segment,widths,height,false);
                    offset+=chunk;
                    if(offset<count) {ensure(20);text("Mục tiêu tiếp ở dòng sau",LEFT,y,8,regular,GREEN);y-=15;}
                }
            }y-=14;
        }
        void row(List<String> cells,boolean header) throws IOException {
            float[] widths={245,100,162};var wrapped=new ArrayList<List<String>>();
            for(int i=0;i<3;i++) wrapped.add(wrap(cells.get(i),widths[i]-12,9,header?bold:regular));
            float height=wrapped.stream().mapToInt(List::size).max().orElse(1)*13+12;
            ensure(height);drawRow(wrapped,widths,height,header);
        }
        void drawRow(List<List<String>> cells,float[] widths,float height,boolean header) throws IOException {
            float x=LEFT;
            for(int i=0;i<3;i++) {
                if(header) {out.setNonStrokingColor(GRAY);out.addRect(x,y-height+5,widths[i],height);out.fill();}
                out.setStrokingColor(GRAY);out.setLineWidth(.5f);out.addRect(x,y-height+5,widths[i],height);out.stroke();
                float baseline=y-9;
                for(String line:cells.get(i)) {text(line,x+6,baseline,9,header?bold:regular,Color.BLACK);baseline-=13;}
                x+=widths[i];
            }y-=height;
        }
        void signatures(String date) throws IOException {
            ensure(140);y-=14;text("Ngày lập báo cáo: "+date,LEFT,y,10,regular,Color.BLACK);y-=30;
            text("GIÁO VIÊN ĐÁNH GIÁ",LEFT+25,y,10,bold,GREEN);
            text("ĐẠI DIỆN TRUNG TÂM",LEFT+285,y,10,bold,GREEN);y-=18;
            text("(Ký và ghi rõ họ tên)",LEFT+35,y,9,regular,Color.BLACK);
            text("(Ký và ghi rõ họ tên)",LEFT+300,y,9,regular,Color.BLACK);y-=70;
        }
        void finish() throws IOException {
            out.close();out=null;
            int count=doc.getNumberOfPages();
            for(int i=0;i<count;i++) try(var footer=new PDPageContentStream(doc,doc.getPage(i),PDPageContentStream.AppendMode.APPEND,true,true)) {
                footer.beginText();footer.setFont(regular,9);footer.setNonStrokingColor(GREEN);footer.newLineAtOffset(LEFT,27);
                footer.showText("Trung tâm Đường An");footer.endText();
                footer.beginText();footer.setFont(regular,9);footer.newLineAtOffset(460,27);footer.showText("Trang "+(i+1)+" / "+count);footer.endText();
            }
        }
    }
}

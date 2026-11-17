package com.example.TTGDHNDuongAn.controller;

import com.example.TTGDHNDuongAn.dto.IndividualReportDtos.*;
import com.example.TTGDHNDuongAn.security.AppUserPrincipal;
import com.example.TTGDHNDuongAn.service.*;
import java.io.IOException;
import java.util.*;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/individual-reports")
public class IndividualReportController {
    private final IndividualReportService service;
    private final IndividualReportPdfService pdf;
    public IndividualReportController(IndividualReportService service,IndividualReportPdfService pdf) {this.service=service;this.pdf=pdf;}
    private AppUserPrincipal actor(Authentication auth) {return (AppUserPrincipal)auth.getPrincipal();}
    @GetMapping("/students")
    public List<Student> students(@RequestParam(defaultValue="") String q,Authentication auth) {return service.students(q,actor(auth));}
    @GetMapping("/students/{studentId}")
    public Student student(@PathVariable String studentId,Authentication auth) {return service.studentInfo(studentId,actor(auth));}
    @GetMapping("/students/{studentId}/reports")
    public List<Summary> reports(@PathVariable String studentId,Authentication auth) {return service.reports(studentId,actor(auth));}
    @GetMapping("/{reportId}")
    public Detail detail(@PathVariable String reportId,Authentication auth) {return service.detail(reportId,actor(auth));}
    @PutMapping("/{reportId}/sections")
    public Detail update(@PathVariable String reportId,@RequestBody Edit edit,Authentication auth) {return service.update(reportId,edit,actor(auth));}
    @GetMapping(value="/{reportId}/pdf",produces=MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> pdf(@PathVariable String reportId,@RequestParam(defaultValue="false") boolean download,Authentication auth) {
        var detail=service.detail(reportId,actor(auth));
        try {
            byte[] bytes=pdf.render(detail);
            return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF).cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION,(download?"attachment":"inline")+"; filename=\"bao-cao-"+detail.report().id()+".pdf\"")
                .header("X-Content-Type-Options","nosniff").body(bytes);
        } catch(IOException ex) {throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,"Không thể tạo PDF. Vui lòng thử lại sau.");}
    }
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String,String>> error(ResponseStatusException ex) {
        return ResponseEntity.status(ex.getStatusCode()).contentType(MediaType.APPLICATION_JSON).cacheControl(CacheControl.noStore())
            .body(Map.of("message",ex.getReason()==null?"Không thể xử lý báo cáo.":ex.getReason()));
    }
}

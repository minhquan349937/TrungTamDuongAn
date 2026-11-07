package com.example.TTGDHNDuongAn.controller;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import com.example.TTGDHNDuongAn.dto.WebsiteDtos.ConsultationInput;
import com.example.TTGDHNDuongAn.service.ConsultationService;
import com.example.TTGDHNDuongAn.service.WebsiteContentService;
import com.example.TTGDHNDuongAn.service.WebsiteMediaService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/** Anonymous API: published content, its media, and sending a consultation request. Nothing here lists or reads requests. */
@RestController
@RequestMapping("/api/public")
public class WebsitePublicController {
    private final WebsiteContentService content;
    private final WebsiteMediaService media;
    private final ConsultationService consultations;

    public WebsitePublicController(WebsiteContentService content, WebsiteMediaService media, ConsultationService consultations) {
        this.content = content;
        this.media = media;
        this.consultations = consultations;
    }

    @GetMapping("/website")
    public ResponseEntity<String> website() {
        String json = content.published().orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Website chưa được xuất bản."));
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).cacheControl(CacheControl.noCache()).body(json);
    }

    @GetMapping("/website/media/{id}")
    public ResponseEntity<Resource> file(@PathVariable String id) {
        var stored = media.openPublished(id);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(stored.contentType()))
                .cacheControl(CacheControl.maxAge(5, TimeUnit.MINUTES).cachePublic()).body(stored.file());
    }

    @PostMapping("/consultations")
    public ResponseEntity<Map<String, String>> consult(@RequestBody ConsultationInput input, HttpServletRequest request) {
        // First X-Forwarded-For hop (tunnel / reverse proxy) else the socket address; only used for the spam throttle.
        String forwarded = request.getHeader("X-Forwarded-For");
        String ip = forwarded == null || forwarded.isBlank() ? request.getRemoteAddr() : forwarded.split(",")[0].trim();
        consultations.submit(input, ip);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("message", "Trung tâm đã nhận yêu cầu và sẽ liên hệ với bạn sớm."));
    }
}

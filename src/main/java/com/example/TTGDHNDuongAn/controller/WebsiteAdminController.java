package com.example.TTGDHNDuongAn.controller;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import com.example.TTGDHNDuongAn.dto.WebsiteDtos.*;
import com.example.TTGDHNDuongAn.security.AppUserPrincipal;
import com.example.TTGDHNDuongAn.service.ConsultationService;
import com.example.TTGDHNDuongAn.service.WebsiteContentService;
import com.example.TTGDHNDuongAn.service.WebsiteMediaService;
import org.springframework.core.io.Resource;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/admin/website")
@PreAuthorize("hasRole('ADMIN')")
public class WebsiteAdminController {
    private final WebsiteContentService content;
    private final WebsiteMediaService media;
    private final ConsultationService consultations;

    public WebsiteAdminController(WebsiteContentService content, WebsiteMediaService media, ConsultationService consultations) {
        this.content = content;
        this.media = media;
        this.consultations = consultations;
    }

    private static String actor(Authentication auth) {
        return ((AppUserPrincipal) auth.getPrincipal()).id();
    }

    @GetMapping("/status") public Status status() { return content.status(); }
    @GetMapping("/preview") public Content preview() { return content.preview(); }
    @PostMapping("/publish") public Status publish(Authentication auth) { return content.publish(actor(auth)); }

    // ---- media (literal paths win over /{resource}) ----
    @GetMapping("/media") public List<AdminMedia> mediaList(@RequestParam(required = false) String albumId) { return media.list(albumId); }

    @PostMapping(value = "/media", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<AdminMedia> upload(@RequestParam MultipartFile file, @RequestParam(required = false) String albumId,
                                             @RequestParam(required = false) String title, @RequestParam(required = false) String altText,
                                             @RequestParam(defaultValue = "false") boolean publicConsent, Authentication auth) {
        return ResponseEntity.status(HttpStatus.CREATED).body(media.upload(file, albumId, title, altText, publicConsent, actor(auth)));
    }

    @PutMapping("/media/order") public ResponseEntity<Void> mediaOrder(@RequestBody Order order) {
        media.reorder(order.ids() == null ? List.of() : order.ids());
        return ResponseEntity.noContent().build();
    }
    @GetMapping("/media/{id}") public AdminMedia mediaGet(@PathVariable String id) { return media.get(id); }
    @PutMapping("/media/{id}") public AdminMedia mediaUpdate(@PathVariable String id, @RequestBody MediaMeta meta, Authentication auth) { return media.update(id, meta, actor(auth)); }
    @DeleteMapping("/media/{id}") public ResponseEntity<Void> mediaDelete(@PathVariable String id) {
        media.delete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/media/{id}/file")
    public ResponseEntity<Resource> mediaFile(@PathVariable String id) {
        var stored = media.open(id);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(stored.contentType())).cacheControl(CacheControl.noStore()).body(stored.file());
    }

    // ---- consultation requests ----
    @GetMapping("/consultations")
    public PageOf<Consultation> consultations(@RequestParam(required = false) String q, @RequestParam(required = false) String status,
                                              @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                              @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                              @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return consultations.list(q, status, from, to, page, size);
    }
    @GetMapping("/consultations/assignees") public List<Assignee> assignees() { return consultations.assignees(); }
    @GetMapping("/consultations/{id}") public Consultation consultation(@PathVariable String id) { return consultations.get(id); }
    @PutMapping("/consultations/{id}")
    public Consultation consultationUpdate(@PathVariable String id, @RequestBody ConsultationUpdate body, Authentication auth) {
        return consultations.update(id, body, actor(auth));
    }

    // ---- draft content: items, services, team, faqs, albums, sections, settings ----
    @GetMapping("/{resource}") public List<Map<String, Object>> list(@PathVariable String resource, @RequestParam(required = false) String section) { return content.list(resource, section); }
    @PostMapping("/{resource}")
    public ResponseEntity<Map<String, Object>> create(@PathVariable String resource, @RequestBody Map<String, Object> body, Authentication auth) {
        return ResponseEntity.status(HttpStatus.CREATED).body(content.save(resource, null, body, actor(auth)));
    }
    @PutMapping("/{resource}/order") public ResponseEntity<Void> order(@PathVariable String resource, @RequestBody Order order) {
        content.reorder(resource, order.ids());
        return ResponseEntity.noContent().build();
    }
    @PutMapping("/{resource}/{id}")
    public Map<String, Object> update(@PathVariable String resource, @PathVariable String id, @RequestBody Map<String, Object> body, Authentication auth) {
        return content.save(resource, id, body, actor(auth));
    }
    @DeleteMapping("/{resource}/{id}") public ResponseEntity<Void> delete(@PathVariable String resource, @PathVariable String id) {
        content.delete(resource, id);
        return ResponseEntity.noContent().build();
    }
}

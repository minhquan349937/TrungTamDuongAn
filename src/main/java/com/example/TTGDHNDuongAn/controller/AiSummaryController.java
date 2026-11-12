package com.example.TTGDHNDuongAn.controller;

import com.example.TTGDHNDuongAn.security.AppUserPrincipal;
import com.example.TTGDHNDuongAn.service.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import java.util.Map;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class AiSummaryController {
    public record Input(String keywords) {
        @Override public String toString() { return "AiSummaryInput[redacted]"; }
    }
    public record Output(String generatedText) {
        @Override public String toString() { return "AiSummaryOutput[redacted]"; }
    }
    private final AiSummaryDataService data;
    private final GeminiSummaryClient client;
    private final AiSummaryQuota quota;
    public AiSummaryController(AiSummaryDataService data,GeminiSummaryClient client,AiSummaryQuota quota) {
        this.data=data;this.client=client;this.quota=quota;
    }
    @PostMapping("/api/learning-plans/{planId}/ai-summary")
    public Output generate(@PathVariable String planId,@RequestBody Input input,Authentication auth) {
        String keywords=input.keywords()==null?"":input.keywords().trim();
        if(keywords.length()>2000) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Ghi nhận tối đa 2000 ký tự.");
        var actor=(AppUserPrincipal)auth.getPrincipal();
        var context=data.load(planId,actor); // Authorization and DB snapshot finish before external I/O.
        client.requireConfigured();
        quota.acquire(actor.id()); // Failed upstream calls count too; no automatic retries.
        return new Output(client.generate(context,keywords));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String,String>> businessError(ResponseStatusException error) {
        return ResponseEntity.status(error.getStatusCode()).body(Map.of("message",error.getReason()==null?"Không thể tạo nhận xét AI.":error.getReason()));
    }
}

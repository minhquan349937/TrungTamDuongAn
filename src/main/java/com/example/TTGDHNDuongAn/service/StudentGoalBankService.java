package com.example.TTGDHNDuongAn.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;
import com.example.TTGDHNDuongAn.dto.GoalBankDtos.*;
import com.example.TTGDHNDuongAn.repository.CatalogIdGenerator.Kind;
import com.example.TTGDHNDuongAn.repository.StudentGoalBankRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import org.springframework.web.server.ResponseStatusException;

@Service
public class StudentGoalBankService {
    private final StudentGoalBankRepository repository;
    private final GoalTargetResolver targets;
    private final ObjectMapper mapper=new ObjectMapper();
    public StudentGoalBankService(StudentGoalBankRepository repository, GoalTargetResolver targets) {
        this.repository=repository; this.targets=targets;
    }
    public List<BankEntry> list(String studentId) { requireStudent(studentId); return repository.list(studentId); }

    @Transactional(readOnly=true)
    public Review preview(String studentId, ReviewRequest request) {
        requireStudent(studentId);
        if(request==null) throw badRequest();
        var selections=resolve(request.programId(),request.selections());
        return new Review(request.programId(),selections,selections.size(),fingerprint(studentId,selections));
    }

    @Transactional
    public Completion complete(String studentId, CompleteRequest request, String actorId) {
        validateStudentId(studentId);
        // The current-read lock is the first query: serializes saves per student without stale RR reads.
        if(!repository.lockStudent(studentId)) throw notFound();
        if(request==null || request.reviewToken()==null || !request.reviewToken().matches("[a-f0-9]{64}")) throw badRequest();
        var selections=resolve(request.programId(),request.selections());
        if(!MessageDigest.isEqual(fingerprint(studentId,selections).getBytes(StandardCharsets.UTF_8),request.reviewToken().getBytes(StandardCharsets.UTF_8)))
            throw new ResponseStatusException(HttpStatus.CONFLICT,"Lựa chọn hoặc thư viện đã thay đổi. Vui lòng xem lại trước khi hoàn tất.");
        var existing=repository.existingTargets(studentId);
        int added=0;
        for(var selection:selections) {
            if(existing.add(selection.key())) { repository.insert(studentId,selection,actorId); added++; }
        }
        return new Completion(added,selections.size()-added,repository.list(studentId));
    }

    private List<Selection> resolve(String programId,List<Target> requests) {
        Kind.PROGRAM.validate(programId);
        if(requests==null || requests.isEmpty() || requests.size()>1000) throw badRequest();
        var selections=new LinkedHashMap<String,Selection>();
        for(var selection:targets.resolveAll(requests)) {
            if(!programId.equals(selection.location().programId()))
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Mục tiêu không thuộc chương trình đã chọn.");
            selections.putIfAbsent(selection.key(),selection);
        }
        return List.copyOf(selections.values());
    }
    private String fingerprint(String studentId,List<Selection> selections) {
        try {
            var canonical=selections.stream().sorted(java.util.Comparator.comparing(Selection::key)).toList();
            byte[] input=(studentId+":"+mapper.writeValueAsString(canonical)).getBytes(StandardCharsets.UTF_8);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(input));
        } catch(NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
    }
    private void requireStudent(String id) { validateStudentId(id); if(!repository.studentExists(id)) throw notFound(); }
    private void validateStudentId(String id) {
        try { UUID.fromString(id); }
        catch(IllegalArgumentException | NullPointerException ex) { throw badRequest(); }
    }
    private ResponseStatusException badRequest() { return new ResponseStatusException(HttpStatus.BAD_REQUEST,"Chọn chương trình và từ 1 đến 1000 mục tiêu hợp lệ."); }
    private ResponseStatusException notFound() { return new ResponseStatusException(HttpStatus.NOT_FOUND,"Không tìm thấy học sinh."); }
}

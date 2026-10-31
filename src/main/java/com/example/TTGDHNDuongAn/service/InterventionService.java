package com.example.TTGDHNDuongAn.service;

import com.example.TTGDHNDuongAn.dto.InterventionDtos.*;
import com.example.TTGDHNDuongAn.dto.GoalSelectionDtos;
import com.example.TTGDHNDuongAn.repository.*;
import com.example.TTGDHNDuongAn.repository.CatalogIdGenerator.Kind;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

@Service
public class InterventionService {
    private final InterventionRepository repository;
    private final StudentPlanRepository plans;
    private final PlanGoalRepository goals;
    private final GoalTargetResolver targets;
    private final GoalCatalogRepository catalog;
    private final GoalSelectionPolicy policy;
    private final StudentGoalHistoryService history;
    private final ObjectMapper mapper=new ObjectMapper();
    public InterventionService(InterventionRepository repository,StudentPlanRepository plans,PlanGoalRepository goals,
            GoalTargetResolver targets,GoalCatalogRepository catalog,GoalSelectionPolicy policy,StudentGoalHistoryService history) {
        this.repository=repository; this.plans=plans; this.goals=goals; this.targets=targets; this.catalog=catalog; this.policy=policy;
        this.history=history;
    }
    public List<StudentSummary> students(String q) {
        if(q==null || q.length()>100) throw bad("Từ khóa tối đa 100 ký tự.");
        return repository.students(q.trim());
    }
    @Transactional(readOnly=true)
    public Workspace workspace(String studentId) {
        uuid(studentId);
        var student=repository.student(studentId).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Không tìm thấy học sinh."));
        var assigned=goals.listForStudent(studentId);
        var byPlan=assigned.stream().collect(java.util.stream.Collectors.groupingBy(com.example.TTGDHNDuongAn.dto.PlanGoalDtos.AssignedGoal::planId));
        var planViews=plans.plans(studentId).stream().map(plan->new PlanView(plan,byPlan.getOrDefault(plan.id(),List.of()))).toList();
        return new Workspace(student,planViews);
    }
    public List<ProgramSummary> programs() { return repository.programs(); }
    public List<DomainGroup> domains(String id) {
        Kind.PROGRAM.validate(id);
        if(!catalog.activeProgramExists(id)) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Không tìm thấy chương trình.");
        var grouped=new LinkedHashMap<String,List<DomainRow>>();
        for(var row:repository.domains(id)) grouped.computeIfAbsent(row.age().domainId(),key->new ArrayList<>()).add(row);
        return grouped.entrySet().stream().map(entry->new DomainGroup(entry.getKey(),entry.getValue().getFirst().code(),entry.getValue().getFirst().name(),
            entry.getValue().stream().map(DomainRow::age).toList())).toList();
    }
    public DomainTargets targets(String id) {
        Kind.LEVEL_DOMAIN.validate(id);
        String code=repository.programCodeForDomain(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Không tìm thấy lĩnh vực."));
        boolean supports=policy.supportsSpecificGoals(code);
        return new DomainTargets(supports,repository.goals(id,supports,"VB".equalsIgnoreCase(code)));
    }
    @Transactional(readOnly=true)
    public Review preview(String studentId,Draft draft) {
        validate(draft);
        uuid(studentId);
        if(repository.student(studentId).isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Không tìm thấy học sinh.");
        history.requireSelectable(studentId,draft.selections());
        var checked=targets.resolveAll(draft.selections());
        var unique=new LinkedHashMap<String,GoalSelectionDtos.Selection>();
        checked.forEach(item->unique.putIfAbsent(item.key(),item));
        return review(studentId,draft,List.copyOf(unique.values()));
    }
    @Transactional
    public Saved complete(String studentId,Complete request,String actorId) {
        uuid(studentId);
        // First SQL is a current-read lock, so two finishes for one student cannot race.
        if(!repository.lockStudent(studentId)) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Không tìm thấy học sinh.");
        if(request==null || request.reviewToken()==null || !request.reviewToken().matches("[a-f0-9]{64}")) throw bad("Vui lòng xem lại mục tiêu trước khi hoàn thành.");
        var draft=new Draft(request.requestId(),request.programId(),request.name(),request.startDate(),request.selections());
        validate(draft);
        var existing=repository.existing(draft.requestId());
        if(existing.isPresent()) {
            var saved=existing.get();
            var assigned=goals.list(draft.requestId());
            var requestedKeys=new HashSet<String>();
            for(var target:draft.selections()) requestedKeys.add(key(target.goalId(),target.specificGoalId()));
            var savedKeys=new HashSet<String>();
            assigned.forEach(g->savedKeys.add(key(g.goalId(),g.specificGoalId())));
            if(!saved.plan().studentId().equals(studentId) || !saved.actorId().equals(actorId)
                    || !saved.plan().name().equals(draft.name().trim()) || !saved.plan().startDate().equals(draft.startDate())
                    || !savedKeys.equals(requestedKeys)) throw conflict("Lượt hoàn tất này đã được dùng cho lựa chọn khác.");
            return new Saved(new PlanView(saved.plan(),assigned),true);
        }
        var checked=preview(studentId,draft);
        if(!MessageDigest.isEqual(checked.reviewToken().getBytes(StandardCharsets.UTF_8),request.reviewToken().getBytes(StandardCharsets.UTF_8)))
            throw conflict("Nội dung hoặc thông tin kế hoạch đã thay đổi. Vui lòng kiểm tra lại trước khi hoàn thành.");
        var plan=repository.insert(studentId,draft,actorId);
        for(var item:checked.items()) goals.insert(plan.id(),item.goalId(),item.specificGoalId(),actorId);
        return new Saved(new PlanView(plan,goals.list(plan.id())),false);
    }
    private String key(String goalId,String specificId) { return specificId==null ? "G:"+goalId : "S:"+specificId+":"+goalId; }
    private void validate(Draft draft) {
        if(draft==null || draft.name()==null || draft.name().isBlank() || draft.name().trim().length()>200 || draft.startDate()==null
            || draft.selections()==null || draft.selections().isEmpty() || draft.selections().size()>1000) throw bad("Nhập tên, ngày bắt đầu và chọn từ 1 đến 1000 mục tiêu.");
        uuid(draft.requestId()); Kind.PROGRAM.validate(draft.programId());
        for(var target:draft.selections()) {
            if(target==null) throw bad("Mục tiêu không hợp lệ.");
            Kind.GOAL.validate(target.goalId()); if(target.specificGoalId()!=null) Kind.SPECIFIC.validate(target.specificGoalId());
        }
    }
    private Review review(String studentId,Draft draft,List<GoalSelectionDtos.Selection> items) {
        try {
            var canonical=items.stream().sorted(Comparator.comparing(GoalSelectionDtos.Selection::key)).toList();
            var input=List.of(studentId,draft.requestId(),draft.programId(),draft.name().trim(),draft.startDate().toString(),canonical);
            var token=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(mapper.writeValueAsString(input).getBytes(StandardCharsets.UTF_8)));
            return new Review(draft.requestId(),draft.programId(),draft.name().trim(),draft.startDate(),items,items.size(),token);
        } catch(NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
    }
    private void uuid(String value) {
        try { if(!UUID.fromString(value).toString().equalsIgnoreCase(value)) throw new IllegalArgumentException(); }
        catch(IllegalArgumentException | NullPointerException ex) { throw bad("Mã yêu cầu hoặc học sinh không hợp lệ."); }
    }
    private ResponseStatusException bad(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST,message); }
    private ResponseStatusException conflict(String message) { return new ResponseStatusException(HttpStatus.CONFLICT,message); }
}

package com.example.TTGDHNDuongAn.service;

import com.example.TTGDHNDuongAn.dto.GoalEvaluationDtos.*;
import com.example.TTGDHNDuongAn.repository.GoalEvaluationRepository;
import com.example.TTGDHNDuongAn.security.AppUserPrincipal;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class GoalEvaluationService {
    private final GoalEvaluationRepository repository;
    private final PlanAccessService access;
    public GoalEvaluationService(GoalEvaluationRepository repository,PlanAccessService access) {
        this.repository=repository;this.access=access;
    }
    @Transactional
    public List<Evaluation> list(String plan,String goal,AppUserPrincipal actor) {
        requireGoal(plan,goal,actor);return repository.list(goal);
    }
    @Transactional
    public List<Evaluation> create(String plan,String goal,Input input,AppUserPrincipal actor) {
        validate(input);requireGoal(plan,goal,actor);
        repository.insert(UUID.randomUUID().toString(),goal,repository.nextAttempt(goal),input,actor.id());
        return repository.list(goal);
    }
    @Transactional
    public List<Evaluation> update(String plan,String goal,String id,Input input,AppUserPrincipal actor) {
        validate(input);uuid(id);requireGoal(plan,goal,actor);
        var evaluation=repository.list(goal).stream().filter(e->e.id().equals(id)).findFirst()
            .orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Không tìm thấy lần đánh giá."));
        if(!"ADMIN".equals(actor.role()) && !evaluation.evaluatedBy().equals(actor.id()))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Bạn chỉ được sửa đánh giá do mình tạo.");
        repository.update(id,input);return repository.list(goal);
    }
    private void requireGoal(String plan,String goal,AppUserPrincipal actor) {
        uuid(plan);uuid(goal);
        String student=repository.lockPlanStudent(plan);
        if(student==null) throw missing();
        access.requireStudent(student,actor);
        if(!repository.lockGoal(plan,goal)) throw missing();
    }
    private void validate(Input input) {
        if(input==null || input.achievementPercent()!=null && (input.achievementPercent().compareTo(BigDecimal.ZERO)<0 ||
            input.achievementPercent().compareTo(new BigDecimal("100"))>0 || input.achievementPercent().scale()>2) ||
            input.restartDecision()!=null && !Set.of("RETRY","NO_RETRY").contains(input.restartDecision()) ||
            input.evaluationNotes()!=null && input.evaluationNotes().getBytes(StandardCharsets.UTF_8).length>65535)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Tỷ lệ phải từ 0 đến 100, tối đa hai chữ số thập phân; quyết định thực hiện lại không hợp lệ.");
    }
    private void uuid(String id) {
        try { if(!UUID.fromString(id).toString().equalsIgnoreCase(id)) throw new IllegalArgumentException(); }
        catch(IllegalArgumentException|NullPointerException ex) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Mã không hợp lệ."); }
    }
    private ResponseStatusException missing() { return new ResponseStatusException(HttpStatus.NOT_FOUND,"Không tìm thấy kế hoạch hoặc mục tiêu trong kế hoạch."); }
}

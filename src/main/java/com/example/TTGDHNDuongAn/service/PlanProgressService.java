package com.example.TTGDHNDuongAn.service;

import com.example.TTGDHNDuongAn.dto.PlanProgressDtos.*;
import com.example.TTGDHNDuongAn.repository.PlanProgressRepository;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@Service
public class PlanProgressService {
    private final PlanProgressRepository repository;
    public PlanProgressService(PlanProgressRepository repository) { this.repository=repository; }
    @Transactional public void edit(String id,EditPlan request) {
        uuid(id);
        if(request==null||request.name()==null||request.name().isBlank()||request.name().length()>200||request.startDate()==null||
            request.status()==null||!Set.of("DRAFT","ACTIVE","COMPLETED","PAUSED","CANCELLED").contains(request.status())) throw bad();
        if(!repository.lockPlan(id)) throw missing();
        repository.edit(id,request);
    }
    @Transactional public void status(String plan,String goal,GoalStatus request) {
        requireGoal(plan,goal);
        if(request==null||request.status()==null||!Set.of("NOT_STARTED","IN_PROGRESS","ACHIEVED","PAUSED").contains(request.status())) throw bad();
        repository.status(goal,request.status());
    }
    @Transactional public List<Progress> observe(String plan,String goal,Observation request,String actor) {
        requireGoal(plan,goal);
        if(request==null||request.notes()==null||request.notes().isBlank()||request.notes().getBytes(java.nio.charset.StandardCharsets.UTF_8).length>65535) throw bad();
        Integer successes=request.successCount(),opportunities=request.opportunityCount();
        if((successes==null)!=(opportunities==null)||successes!=null&&(successes<0||opportunities<0||successes>opportunities)) throw bad();
        repository.observe(goal,request,actor);
        return repository.list(plan,goal);
    }
    @Transactional public List<Progress> list(String plan,String goal) { requireGoal(plan,goal); return repository.list(plan,goal); }
    private void requireGoal(String plan,String goal) {
        uuid(plan);uuid(goal);
        // Same lock order as plan editing; prevents cross-plan reads and observations.
        if(!repository.lockPlan(plan)||!repository.lockGoal(plan,goal)) throw missing();
    }
    private void uuid(String id) { try { if(!UUID.fromString(id).toString().equals(id)) throw bad(); } catch(IllegalArgumentException|NullPointerException ex) {throw bad();} }
    private ResponseStatusException missing() { return new ResponseStatusException(HttpStatus.NOT_FOUND,"Plan or assigned goal not found"); }
    private ResponseStatusException bad() { return new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid plan or observation data"); }
}

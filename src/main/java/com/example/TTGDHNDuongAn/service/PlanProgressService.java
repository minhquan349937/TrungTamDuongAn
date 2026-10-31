package com.example.TTGDHNDuongAn.service;
import com.example.TTGDHNDuongAn.dto.PlanProgressDtos.EditPlan;
import com.example.TTGDHNDuongAn.dto.PlanProgressDtos.ChangeStatus;
import com.example.TTGDHNDuongAn.repository.PlanProgressRepository;
import com.example.TTGDHNDuongAn.security.AppUserPrincipal;
import java.util.Set;
import java.util.UUID;
import java.time.LocalDate;
import java.time.ZoneId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
@Service
public class PlanProgressService {
    private final PlanProgressRepository repository;
    private final PlanAccessService access;
    public PlanProgressService(PlanProgressRepository repository,PlanAccessService access) { this.repository=repository;this.access=access; }
    @Transactional public void edit(String id,EditPlan request,AppUserPrincipal actor) {
        try { UUID.fromString(id); } catch(IllegalArgumentException|NullPointerException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Mã kế hoạch không hợp lệ.");
        }
        if(request==null||request.name()==null||request.name().isBlank()||request.name().length()>200||request.startDate()==null||
            request.status()==null||!Set.of("DRAFT","ACTIVE","COMPLETED","PAUSED","CANCELLED").contains(request.status()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Thông tin kế hoạch không hợp lệ.");
        String student=repository.lockPlanStudent(id);
        if(student==null) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Không tìm thấy kế hoạch.");
        access.requireStudent(student,actor);
        requireCompletionEvaluation(id,request.status(),request.startDate());
        repository.edit(id,request);
    }
    @Transactional public void changeStatus(String id,ChangeStatus request,AppUserPrincipal actor) {
        try { UUID.fromString(id); } catch(IllegalArgumentException|NullPointerException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Mã kế hoạch không hợp lệ.");
        }
        if(request==null || request.status()==null || !Set.of("PAUSED","COMPLETED").contains(request.status()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Trạng thái kế hoạch không hợp lệ.");
        String student=repository.lockPlanStudent(id);
        if(student==null) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Không tìm thấy kế hoạch.");
        access.requireStudent(student,actor);
        var current=repository.state(id);
        if(Set.of("COMPLETED","CANCELLED").contains(current.status()) || current.status().equals(request.status()))
            throw new ResponseStatusException(HttpStatus.CONFLICT,"Kế hoạch đã ở trạng thái kết thúc hoặc đã cập nhật.");
        requireCompletionEvaluation(id,request.status(),current.startDate());
        repository.edit(id,new EditPlan(current.name(),current.startDate(),request.status()));
    }
    private void requireCompletionEvaluation(String id,String status,LocalDate startDate) {
        if(!"COMPLETED".equals(status)) return;
        if(!repository.hasEvaluation(id)) throw new ResponseStatusException(HttpStatus.CONFLICT,
            "Cần lưu đánh giá cả kế hoạch trước khi hoàn thành kế hoạch.");
        if(startDate.isAfter(LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh"))))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Chưa đến ngày áp dụng kế hoạch.");
    }
}

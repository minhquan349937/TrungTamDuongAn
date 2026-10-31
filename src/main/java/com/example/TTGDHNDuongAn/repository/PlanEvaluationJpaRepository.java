package com.example.TTGDHNDuongAn.repository;

import com.example.TTGDHNDuongAn.model.plan.PlanEvaluation;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PlanEvaluationJpaRepository extends JpaRepository<PlanEvaluation,String> {
    List<PlanEvaluation> findByLearningPlanIdOrderByEvaluatedAtDesc(String planId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from PlanEvaluation e where e.id=:id")
    Optional<PlanEvaluation> lockById(@Param("id") String id);
}

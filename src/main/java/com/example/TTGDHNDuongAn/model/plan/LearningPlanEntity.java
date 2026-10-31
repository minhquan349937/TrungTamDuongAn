package com.example.TTGDHNDuongAn.model.plan;

import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name="learning_plans")
public class LearningPlanEntity {
    @Id @Column(name="id",columnDefinition="char(36)") private String id;
    @OneToMany(mappedBy="learningPlan") private List<PlanEvaluation> evaluations=new ArrayList<>();
    protected LearningPlanEntity() {}
    public String getId() { return id; }
    public List<PlanEvaluation> getEvaluations() { return evaluations; }
}

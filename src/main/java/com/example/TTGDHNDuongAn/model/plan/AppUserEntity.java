package com.example.TTGDHNDuongAn.model.plan;

import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name="app_user")
public class AppUserEntity {
    @Id @Column(name="id",columnDefinition="char(36)") private String id;
    @OneToMany(mappedBy="evaluatedBy") private List<PlanEvaluation> planEvaluations=new ArrayList<>();
    protected AppUserEntity() {}
    public String getId() { return id; }
    public List<PlanEvaluation> getPlanEvaluations() { return planEvaluations; }
}

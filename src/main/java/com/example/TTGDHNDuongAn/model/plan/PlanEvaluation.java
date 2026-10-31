package com.example.TTGDHNDuongAn.model.plan;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name="plan_evaluations")
public class PlanEvaluation {
    @Id @Column(name="id",columnDefinition="char(36)") private String id;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="learning_plan_id",nullable=false) private LearningPlanEntity learningPlan;
    @Column(name="evaluation_notes",nullable=false,columnDefinition="TEXT") private String evaluationNotes;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="evaluated_by",nullable=false) private AppUserEntity evaluatedBy;
    @Column(name="evaluated_at",nullable=false) private LocalDateTime evaluatedAt;
    @Column(name="sent_to_parent",nullable=false) private boolean sentToParent;
    @Column(name="sent_at") private LocalDateTime sentAt;
    @Column(name="updated_at",nullable=false) private LocalDateTime updatedAt;
    protected PlanEvaluation() {}
    public PlanEvaluation(LearningPlanEntity plan,AppUserEntity actor,String notes,LocalDateTime now) {
        id=UUID.randomUUID().toString();learningPlan=plan;evaluatedBy=actor;
        evaluationNotes=notes;evaluatedAt=now;updatedAt=now;
    }
    public String getId(){return id;}
    public LearningPlanEntity getLearningPlan(){return learningPlan;}
    public AppUserEntity getEvaluatedBy(){return evaluatedBy;}
    public String getEvaluationNotes(){return evaluationNotes;}
    public LocalDateTime getEvaluatedAt(){return evaluatedAt;}
    public boolean isSentToParent(){return sentToParent;}
    public LocalDateTime getSentAt(){return sentAt;}
    public LocalDateTime getUpdatedAt(){return updatedAt;}
    public void revise(String notes,LocalDateTime now){evaluationNotes=notes;updatedAt=now;}
    public void publish(LocalDateTime now){sentToParent=true;sentAt=now;updatedAt=now;}
}

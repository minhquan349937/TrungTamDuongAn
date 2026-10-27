package com.example.TTGDHNDuongAn.model.catalog;

import jakarta.persistence.*;

@Entity
@Table(name="specific_goals")
public class CatalogSpecificGoal {
    @Id @Column(length=8) private String id;
    @Column(name="is_active",nullable=false) private boolean active;
    public boolean isActive() { return active; }
    private String code;
    @Column(columnDefinition="TEXT")
    private String description;
    private Integer ageMinMonths;
    private Integer ageMaxMonths;
    private int displayOrder;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="goal_id",nullable=false)
    private CatalogGoal goal;
    protected CatalogSpecificGoal() { }
    public String getId() { return id; }
    public String getCode() { return code; }
    public String getDescription() { return description; }
    public Integer getAgeMinMonths() { return ageMinMonths; }
    public Integer getAgeMaxMonths() { return ageMaxMonths; }
    public int getDisplayOrder() { return displayOrder; }
    public CatalogGoal getGoal() { return goal; }
}

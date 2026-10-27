package com.example.TTGDHNDuongAn.model.catalog;

import jakarta.persistence.*;

@Entity
@Table(name="goals")
public class CatalogGoal {
    @Id @Column(length=8) private String id;
    @Column(name="is_active",nullable=false) private boolean active;
    public boolean isActive() { return active; }
    private int displayOrder;
    public int getDisplayOrder() { return displayOrder; }
    private String code;
    @Column(columnDefinition="TEXT")
    private String name;
    @Column(columnDefinition="TEXT")
    private String description;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="level_domain_id",nullable=false)
    private CatalogLevelDomain levelDomain;
    protected CatalogGoal() { }
    public String getId() { return id; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public CatalogLevelDomain getLevelDomain() { return levelDomain; }
}

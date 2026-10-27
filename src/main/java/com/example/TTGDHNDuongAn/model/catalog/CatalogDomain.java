package com.example.TTGDHNDuongAn.model.catalog;

import jakarta.persistence.*;

@Entity
@Table(name="domains")
public class CatalogDomain {
    @Id @Column(length=8) private String id;
    @Column(name="is_active",nullable=false) private boolean active;
    public boolean isActive() { return active; }
    private String code;
    private String name;
    private int displayOrder;
    public int getDisplayOrder() { return displayOrder; }
    @Column(columnDefinition="TEXT")
    private String description;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="program_id",nullable=false)
    private CatalogProgram program;
    protected CatalogDomain() { }
    public String getId() { return id; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public CatalogProgram getProgram() { return program; }
}

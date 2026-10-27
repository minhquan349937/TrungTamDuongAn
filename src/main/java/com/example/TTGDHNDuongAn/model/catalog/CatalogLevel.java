package com.example.TTGDHNDuongAn.model.catalog;

import jakarta.persistence.*;

@Entity
@Table(name="program_levels")
public class CatalogLevel {
    @Id @Column(length=8) private String id;
    private int levelNumber;
    private String name;
    private int ageMinMonths;
    private int ageMaxMonths;
    private int displayOrder;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="program_id",nullable=false)
    private CatalogProgram program;
    protected CatalogLevel() { }
    public String getId() { return id; }
    public int getLevelNumber() { return levelNumber; }
    public String getName() { return name; }
    public int getAgeMinMonths() { return ageMinMonths; }
    public int getAgeMaxMonths() { return ageMaxMonths; }
    public int getDisplayOrder() { return displayOrder; }
    public CatalogProgram getProgram() { return program; }
}

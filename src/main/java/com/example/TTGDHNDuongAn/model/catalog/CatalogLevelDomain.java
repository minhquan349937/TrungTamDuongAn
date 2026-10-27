package com.example.TTGDHNDuongAn.model.catalog;

import jakarta.persistence.*;

@Entity
@Table(name="program_level_domains")
public class CatalogLevelDomain {
    @Id @Column(length=8) private String id;
    private int displayOrder;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="program_level_id",nullable=false)
    private CatalogLevel level;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="domain_id",nullable=false)
    private CatalogDomain domain;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="program_id",nullable=false)
    private CatalogProgram program;
    protected CatalogLevelDomain() { }
    public String getId() { return id; }
    public int getDisplayOrder() { return displayOrder; }
    public CatalogLevel getLevel() { return level; }
    public CatalogDomain getDomain() { return domain; }
    public CatalogProgram getProgram() { return program; }
}

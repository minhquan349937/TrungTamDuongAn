package com.example.TTGDHNDuongAn.model.catalog;

import jakarta.persistence.*;

@Entity
@Table(name="programs")
public class CatalogProgram {
    @Id @Column(length=8) private String id;
    @Column(name="is_active",nullable=false) private boolean active;
    public boolean isActive() { return active; }
    private String code;
    private String name;
    @Column(columnDefinition="TEXT")
    private String description;
    protected CatalogProgram() { }
    public String getId() { return id; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public String getDescription() { return description; }
}

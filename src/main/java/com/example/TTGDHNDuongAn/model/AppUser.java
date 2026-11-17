package com.example.TTGDHNDuongAn.model;

public record AppUser(
        String id,
        String email,
        String displayName,
        String role,
        String passwordHash,
        String status, String phone) {
    public AppUser(String id,String email,String displayName,String role,String passwordHash,String status) {
        this(id,email,displayName,role,passwordHash,status,null);
    }
}

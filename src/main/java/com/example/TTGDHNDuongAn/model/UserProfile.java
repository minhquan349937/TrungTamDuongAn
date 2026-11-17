package com.example.TTGDHNDuongAn.model;

public record UserProfile(String id, String email, String displayName, String role, String phone) {
    public UserProfile(String id,String email,String displayName,String role) {this(id,email,displayName,role,null);}
}

package com.example.TTGDHNDuongAn.model;

public record AppUser(
        String id,
        String email,
        String displayName,
        String role,
        String passwordHash,
        String status) {
}

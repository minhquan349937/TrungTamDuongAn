package com.example.TTGDHNDuongAn.security;

import java.util.Collection;
import java.util.List;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

public record AppUserPrincipal(
        String id,
        String email,
        String displayName,
        String role,
        String passwordHash, String phone) implements UserDetails {

    public AppUserPrincipal(String id,String email,String displayName,String role,String passwordHash) {
        this(id,email,displayName,role,passwordHash,null);
    }

    @Override public String toString() {return "AppUserPrincipal[id="+id+", role="+role+"]";}

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role));
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return email!=null?email:phone!=null?phone:id;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }
}

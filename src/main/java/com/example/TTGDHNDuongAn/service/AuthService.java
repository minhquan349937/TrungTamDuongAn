package com.example.TTGDHNDuongAn.service;

import java.util.Locale;

import com.example.TTGDHNDuongAn.model.AppUser;
import com.example.TTGDHNDuongAn.model.UserProfile;
import com.example.TTGDHNDuongAn.security.AppUserPrincipal;
import com.example.TTGDHNDuongAn.repository.AppUserRepository;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

@Service
public class AuthService {
    private final AuthenticationManager authenticationManager;
    private final AppUserRepository userRepository;

    public AuthService(AuthenticationManager authenticationManager, AppUserRepository userRepository) {
        this.authenticationManager = authenticationManager;
        this.userRepository = userRepository;
    }

    public Authentication authenticate(String email, String password) {
        Authentication authentication = authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(
                        email.trim().toLowerCase(Locale.ROOT), password));
        AppUserPrincipal principal = (AppUserPrincipal) authentication.getPrincipal();
        userRepository.updateLastLoginAt(principal.id());
        return authentication;
    }

    public UserProfile currentUser(Authentication authentication) {
        AppUserPrincipal principal = (AppUserPrincipal) authentication.getPrincipal();
        return new UserProfile(principal.id(), principal.email(), principal.displayName(), principal.role());
    }
}

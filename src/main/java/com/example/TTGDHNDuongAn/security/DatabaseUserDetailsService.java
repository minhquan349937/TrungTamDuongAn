package com.example.TTGDHNDuongAn.security;

import java.util.Locale;

import com.example.TTGDHNDuongAn.model.AppUser;
import com.example.TTGDHNDuongAn.repository.AppUserRepository;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class DatabaseUserDetailsService implements UserDetailsService {
    private final AppUserRepository userRepository;

    public DatabaseUserDetailsService(AppUserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public AppUserPrincipal loadUserByUsername(String email) throws UsernameNotFoundException {
        AppUser user = userRepository.findActiveByEmail(email.trim().toLowerCase(Locale.ROOT))
                .orElseThrow(() -> new UsernameNotFoundException("Account not found"));
        return new AppUserPrincipal(user.id(), user.email(), user.displayName(), user.role(), user.passwordHash());
    }
}

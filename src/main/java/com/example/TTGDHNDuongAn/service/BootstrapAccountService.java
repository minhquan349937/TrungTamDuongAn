package com.example.TTGDHNDuongAn.service;

import java.util.List;

import com.example.TTGDHNDuongAn.model.AppUser;
import com.example.TTGDHNDuongAn.repository.AppUserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class BootstrapAccountService {
    private final AppUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public BootstrapAccountService(AppUserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public void createIfMissing(List<BootstrapAccount> accounts) {
        for (BootstrapAccount account : accounts) {
            if (account.email().isBlank() || account.password().isBlank()) {
                throw new IllegalStateException("Bootstrap account email/password cannot be blank");
            }
            userRepository.insertIfAbsent(new AppUser(null, account.email(), account.displayName(), account.role(),
                    passwordEncoder.encode(account.password()), "ACTIVE"));
        }
    }

    public record BootstrapAccount(String email, String password, String displayName, String role) {
    }
}

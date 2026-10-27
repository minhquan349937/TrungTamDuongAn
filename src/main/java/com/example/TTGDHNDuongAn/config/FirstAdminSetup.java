package com.example.TTGDHNDuongAn.config;

import java.util.List;

import com.example.TTGDHNDuongAn.service.BootstrapAccountService;
import com.example.TTGDHNDuongAn.service.BootstrapAccountService.BootstrapAccount;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class FirstAdminSetup implements ApplicationRunner {
    private final BootstrapAccountService accountService;
    private final String email;
    private final String password;

    public FirstAdminSetup(BootstrapAccountService accountService,
                           @Value("${SETUP_ADMIN_EMAIL:}") String email,
                           @Value("${SETUP_ADMIN_PASSWORD:}") String password) {
        this.accountService = accountService;
        this.email = email;
        this.password = password;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (email.isBlank() && password.isBlank()) return;
        if (email.isBlank() || password.isBlank())
            throw new IllegalStateException("Set both SETUP_ADMIN_EMAIL and SETUP_ADMIN_PASSWORD");
        accountService.createIfMissing(List.of(
                new BootstrapAccount(email, password, "Quản trị viên", "ADMIN")));
    }
}

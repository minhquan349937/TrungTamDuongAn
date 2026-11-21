package com.example.TTGDHNDuongAn.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    AuthenticationManager authenticationManager(
            UserDetailsService userDetailsService,
            PasswordEncoder passwordEncoder) {
        var provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return new ProviderManager(provider);
    }

    @Bean
    SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            SecurityContextRepository securityContextRepository, JdbcTemplate jdbc) throws Exception {
        http
                .addFilterBefore(new AccountSessionFilter(jdbc), org.springframework.security.web.access.intercept.AuthorizationFilter.class)
                // Anonymous visitors have no session to forge; the endpoint is protected by validation and throttling instead.
                .csrf(csrf -> csrf.csrfTokenRepository(new HttpSessionCsrfTokenRepository())
                        .ignoringRequestMatchers("/api/public/consultations"))
                .securityContext(context -> context.securityContextRepository(securityContextRepository))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(HttpMethod.GET, "/api/auth/csrf").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()
                        .requestMatchers("/api/auth/me", "/api/auth/logout").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/schedule").hasAnyRole("ADMIN", "TEACHER", "PARENT")
                        .requestMatchers(HttpMethod.PUT, "/api/schedule/sessions/*/attendance").hasAnyRole("ADMIN", "TEACHER")
                        .requestMatchers("/api/schedule/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/teacher/portal").hasRole("TEACHER")
                        .requestMatchers("/api/teacher/portal").denyAll()
                        .requestMatchers(HttpMethod.GET, "/api/parent/portal").hasRole("PARENT")
                        .requestMatchers("/api/parent/portal").denyAll()
                        .requestMatchers(HttpMethod.GET, "/api/fees", "/api/fees/students").hasAnyRole("ADMIN", "PARENT")
                        .requestMatchers("/api/fees", "/api/fees/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/public/website", "/api/public/website/media/*").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/public/consultations").permitAll()
                        .requestMatchers("/api/admin/website/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/programs", "/api/programs/*/levels",
                                "/api/programs/*/domains", "/api/levels/*/domains", "/api/level-domains/*/goals", "/api/goals/*").authenticated()
                        .requestMatchers("/api/programs", "/api/programs/*/levels", "/api/programs/*/domains", "/api/levels/*/domain-links", "/api/levels/*/domains",
                                "/api/level-domains/*/goals", "/api/goals/*", "/api/goals/*/specific-goals",
                                "/api/goals/*/specific-goals/*").hasRole("ADMIN")
                        .requestMatchers("/api/plans/*/goals/*/evaluations", "/api/plans/*/goals/*/evaluations/*").hasAnyRole("ADMIN", "TEACHER")
                        .requestMatchers(HttpMethod.GET, "/api/plans/*/goals").hasAnyRole("ADMIN", "TEACHER")
                        .requestMatchers(HttpMethod.GET, "/api/plans/*/excel").hasAnyRole("ADMIN", "TEACHER")
                        .requestMatchers(HttpMethod.GET, "/api/plans/*/available-goals", "/api/plans/*/available-goals/*").hasAnyRole("ADMIN", "TEACHER")
                        .requestMatchers(HttpMethod.POST, "/api/plans/*/goals").hasAnyRole("ADMIN", "TEACHER")
                        .requestMatchers(HttpMethod.PUT, "/api/plans/*/goals/*/replace").hasAnyRole("ADMIN", "TEACHER")
                        .requestMatchers(HttpMethod.PATCH, "/api/plans/*").hasAnyRole("ADMIN", "TEACHER")
                        .requestMatchers(HttpMethod.PATCH, "/api/plans/*/status").hasAnyRole("ADMIN", "TEACHER")
                        .requestMatchers(HttpMethod.POST, "/api/learning-plans/*/ai-summary").hasAnyRole("ADMIN", "TEACHER")
                        .requestMatchers("/api/individual-reports/**").hasAnyRole("ADMIN", "TEACHER")
                        .requestMatchers(HttpMethod.GET, "/api/learning-plans/*/evaluations", "/api/plan-evaluations/*").hasAnyRole("ADMIN", "TEACHER", "PARENT")
                        .requestMatchers("/api/learning-plans/*/evaluations", "/api/plan-evaluations/*", "/api/plan-evaluations/*/send").hasAnyRole("ADMIN", "TEACHER")
                        .requestMatchers(HttpMethod.GET, "/api/intervention/**").hasAnyRole("ADMIN", "TEACHER")
                        .requestMatchers("/api/suggested-activities/**").hasAnyRole("ADMIN", "TEACHER")
                        .requestMatchers("/api/plans/**").hasRole("ADMIN")
                        .requestMatchers("/api/intervention/**").hasRole("ADMIN")
                        .requestMatchers("/api/student-profiles", "/api/student-profiles/**").hasRole("ADMIN")
                        .requestMatchers("/api/staff-profiles", "/api/staff-profiles/**").hasRole("ADMIN")
                        .requestMatchers("/api/guardians/**").hasRole("ADMIN")
                        .requestMatchers("/api/students", "/api/students/*/plans").hasRole("ADMIN")
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .requestMatchers("/api/teacher/**").hasAnyRole("ADMIN", "TEACHER")
                        .requestMatchers("/api/parent/**").hasAnyRole("ADMIN", "PARENT")
                        .requestMatchers("/api/**").denyAll()
                        .anyRequest().permitAll())
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, exception) -> response.sendError(401))
                        .accessDeniedHandler((request, response, exception) -> response.sendError(403)));
        return http.build();
    }
}
